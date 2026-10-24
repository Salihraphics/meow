package com.salihraphics.meow.feature.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.hypot

/** One pointer in one event, as [TouchArbiter] sees it (positions in viewport pixels). */
data class TouchSample(
    val id: Long,
    val position: Offset,
    val pressed: Boolean,
    val stylus: Boolean,
    val uptime: Long,
)

/** What the viewer does with one pointer event. */
sealed interface TouchAction {
    /** Not ours: single-finger scrolling, taps and drawing stay with the list and the page. */
    data object Pass : TouchAction

    /** Consume touches and do nothing: a palm or resting hand while the stylus writes. */
    data object Block : TouchAction

    /** Two or more fingers: scale by [zoom] about [from] and move [from] to [to] (pinch and two-finger pan). */
    data class Transform(val zoom: Float, val from: Offset, val to: Offset) : TouchAction

    /** One finger moves the pages by [delta] because the list can't follow it (see [TouchArbiter]). */
    data class Pan(val delta: Offset) : TouchAction

    /** The fingers are up after a [Transform]; apply it for real. */
    data object EndTransform : TouchAction

    /** The fingers are up after a [Pan]; keep moving at [velocity]. */
    data class EndPan(val velocity: Velocity) : TouchAction
}

/**
 * Decides who owns a touch on the pages. The list only follows the first finger that touched
 * it, and a naive "two fingers = pinch" turned a thumb holding the tablet into a zoom. So:
 *
 * - One finger, or a stylus: the list (scrolling) or the page (drawing), untouched.
 * - A finger that has rested still for [restingMs] before another lands is a hand holding the
 *   device, not part of a gesture; it is ignored. If the other finger then moves, the pages
 *   follow that finger here, since the list would wait for the resting one.
 * - Two fingers become a pinch/pan only when they mean it: they landed together (within
 *   [togetherMs]) and the span or centre moved past the touch slop, or both of them moved.
 *   A finger landing on a list already scrolling with another doesn't stop the scroll.
 * - While [drawing], touches that were down while the stylus wrote, or landed just after it,
 *   are the writing hand: blocked for as long as they stay down, between strokes too, so a
 *   resting or sliding palm never scrolls or zooms.
 */
class TouchArbiter(
    private val slop: Float,
    private val restingMs: Long = 250,
    private val togetherMs: Long = 150,
    /** How near the last stylus point a touch landing just after it must be to count as the palm. */
    private val palmReach: Float = slop * 25,
) {
    private class Finger(val id: Long, val downAt: Long, val downPos: Offset, var pos: Offset) {
        var resting = false

        /** Another finger was already moving (scrolling) when this one landed. */
        var landedOnAMove = false
        val moved get() = hypot(pos.x - downPos.x, pos.y - downPos.y)
    }

    private val fingers = LinkedHashMap<Long, Finger>()

    /** Touches that are the writing hand: down while the stylus was, or landing just after it. */
    private val palms = HashSet<Long>()
    private var lastStylusAt = Long.MIN_VALUE / 2
    private var lastStylusPos = Offset.Zero
    private var mode: Mode = Mode.Idle
    private var lastCentroid = Offset.Zero
    private var lastSpan = 0f
    private var lastSet: Set<Long> = emptySet()
    private var panFinger: Long? = null
    private var velocity = VelocityTracker()

    private enum class Mode { Idle, Pass, Transform, Pan }

    private companion object {
        /** A touch landing this soon after the stylus is the hand that holds it. */
        const val PALM_AFTER_STYLUS_MS = 400L
    }

    /** True while two fingers own the gesture (or one remains after a pinch). */
    val transforming: Boolean get() = mode == Mode.Transform

    fun onEvent(samples: List<TouchSample>, drawing: Boolean): TouchAction {
        val stylusDown = samples.any { it.stylus && it.pressed }
        samples.filter { it.stylus }.forEach {
            lastStylusAt = maxOf(lastStylusAt, it.uptime)
            lastStylusPos = it.position
        }
        val now = samples.maxOfOrNull { it.uptime } ?: lastStylusAt
        if (drawing) {
            samples.filter { !it.stylus && it.pressed }.forEach { s ->
                val landing = s.id !in fingers && s.id !in palms
                // Down while the stylus writes: the hand holding it. Landing right after, next to
                // where it wrote: the same hand coming to rest. Elsewhere: the other hand, scrolling.
                val restingHand = landing && now - lastStylusAt <= PALM_AFTER_STYLUS_MS && (s.position - lastStylusPos).getDistance() <= palmReach
                if (stylusDown || restingHand) palms += s.id
            }
        }
        // The writing hand never takes part in scrolling or zooming, for as long as it stays down.
        val palmSamples = samples.filter { !it.stylus && it.id in palms }
        palmSamples.filter { !it.pressed }.forEach { palms.remove(it.id) }
        if (drawing && (stylusDown || (palmSamples.isNotEmpty() && samples.none { !it.stylus && it.id !in palms && it.pressed }))) {
            if (fingers.isEmpty() && palms.isEmpty() && !stylusDown) reset()
            return TouchAction.Block
        }
        val touches = samples.filter { !it.stylus && it.id !in palms }
        // New fingers: anything already down and still is now a resting hand.
        touches.filter { it.pressed && it.id !in fingers }.forEach { s ->
            fingers.values.forEach { f -> if (!f.resting && s.uptime - f.downAt >= restingMs && f.moved < slop) f.resting = true }
            val moving = fingers.values.any { !it.resting && it.moved >= slop }
            fingers[s.id] = Finger(s.id, s.uptime, s.position, s.position).apply { landedOnAMove = moving }
        }
        touches.forEach { s -> fingers[s.id]?.pos = s.position }
        // A resting finger that starts moving takes part again.
        fingers.values.forEach { if (it.resting && it.moved >= slop) it.resting = false }

        val lifted = touches.filter { !it.pressed }.map { it.id }.toSet()
        val result = decide(drawing, stylusDown, lifted, touches)
        lifted.forEach { fingers.remove(it) }
        if (fingers.isEmpty() && !stylusDown) reset()
        return result
    }

    private fun decide(drawing: Boolean, stylusDown: Boolean, lifted: Set<Long>, touches: List<TouchSample>): TouchAction {
        val active = fingers.values.filter { !it.resting && it.id !in lifted }

        when (mode) {
            Mode.Transform -> {
                if (active.isEmpty()) {
                    return TouchAction.EndTransform.also { mode = Mode.Idle }
                }
                return transformStep(active)
            }
            Mode.Pan -> {
                val finger = fingers[panFinger]
                if (finger == null || panFinger in lifted) {
                    // The lift counts: a finger that paused before lifting doesn't fling.
                    touches.firstOrNull { it.id == panFinger }?.let { velocity.addPosition(it.uptime, it.position) }
                    val v = velocity.calculateVelocity()
                    mode = Mode.Idle
                    return TouchAction.EndPan(v)
                }
                if (active.size >= 2) return startTransform(active)
                return panStep(finger, touches)
            }
            else -> Unit
        }

        if (active.size >= 2) {
            val (a, b) = active.takeLast(2)
            // Together: both landed at once, not one finger joining a scroll already going.
            val together = abs(a.downAt - b.downAt) <= togetherMs && !a.landedOnAMove && !b.landedOnAMove
            val spanNow = span(active)
            val spanAtDown = hypot(a.downPos.x - b.downPos.x, a.downPos.y - b.downPos.y)
            val centreNow = centroid(active)
            val centreAtDown = Offset((a.downPos.x + b.downPos.x) / 2f, (a.downPos.y + b.downPos.y) / 2f)
            val meant = if (together) {
                abs(spanNow - spanAtDown) > slop || (centreNow - centreAtDown).getDistance() > slop
            } else {
                a.moved >= slop && b.moved >= slop
            }
            if (meant) return startTransform(active, fromDown = together)
            return TouchAction.Pass
        }

        // One active finger with a resting one still down: the list is waiting on the resting
        // finger, so the pages follow the active one from here.
        val restingDown = fingers.values.any { it.resting && it.id !in lifted }
        if (active.size == 1 && restingDown && active[0].moved >= slop && mode != Mode.Pass) {
            mode = Mode.Pan
            panFinger = active[0].id
            velocity = VelocityTracker()
            // From where the finger landed, so the pages follow it from its first millimetre.
            lastCentroid = active[0].downPos
            return panStep(active[0], touches)
        }
        if (active.size == 1 && !restingDown && active[0].moved >= slop) mode = Mode.Pass
        return TouchAction.Pass
    }

    /**
     * [fromDown]: the fingers landed together and nothing moved the pages yet, so the whole
     * movement since they landed counts (the pages then follow the fingers exactly). Otherwise
     * the list already scrolled with one of them, and only movement from now on counts.
     */
    private fun startTransform(active: List<Finger>, fromDown: Boolean = false): TouchAction {
        mode = Mode.Transform
        if (!fromDown) {
            lastSet = emptySet()
            return transformStep(active)
        }
        lastSet = active.map { it.id }.toSet()
        val downs = active.map { Finger(it.id, it.downAt, it.downPos, it.downPos) }
        lastCentroid = centroid(downs)
        lastSpan = span(downs)
        return transformStep(active)
    }

    private fun transformStep(active: List<Finger>): TouchAction {
        val ids = active.map { it.id }.toSet()
        val centre = centroid(active)
        val spanNow = span(active)
        if (ids != lastSet) {
            // Fingers joined or left: carry on from here without a jump.
            lastSet = ids
            lastCentroid = centre
            lastSpan = spanNow
            return TouchAction.Transform(1f, centre, centre)
        }
        val zoom = if (active.size >= 2 && lastSpan > 0f && spanNow > 0f) spanNow / lastSpan else 1f
        val action = TouchAction.Transform(zoom, lastCentroid, centre)
        lastCentroid = centre
        lastSpan = spanNow
        return action
    }

    private fun panStep(finger: Finger, touches: List<TouchSample>): TouchAction {
        val now = touches.firstOrNull { it.id == finger.id }
        if (now != null) velocity.addPosition(now.uptime, now.position)
        val delta = finger.pos - lastCentroid
        lastCentroid = finger.pos
        return TouchAction.Pan(delta)
    }

    private fun reset() {
        mode = Mode.Idle
        panFinger = null
        lastSet = emptySet()
    }

    private fun centroid(fs: List<Finger>): Offset =
        Offset(fs.sumOf { it.pos.x.toDouble() }.toFloat() / fs.size, fs.sumOf { it.pos.y.toDouble() }.toFloat() / fs.size)

    private fun span(fs: List<Finger>): Float {
        if (fs.size < 2) return 0f
        val c = centroid(fs)
        return fs.sumOf { hypot(it.pos.x - c.x, it.pos.y - c.y).toDouble() }.toFloat() / fs.size * 2f
    }
}
