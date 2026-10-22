package com.salihraphics.meow.core.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.salihraphics.meow.core.designsystem.theme.CatPalette
import kotlin.math.PI
import kotlin.math.sin

/** Per-frame values from [MeowCat]'s animations. All neutral by default (static cat). */
internal data class CatPose(
    /** 1 = eyes fully open, ~0 = mid-blink. */
    val eyeOpen: Float = 1f,
    val tailAngle: Float = 0f,
    val headTilt: Float = 0f,
    /** 0..1 breathing cycle. */
    val breath: Float = 0f,
    /** 0..1 looping phase for Zzz and sparkles. */
    val phase: Float = 0f,
)

internal data class CatColors(val rim: Color, val accent: Color)

/**
 * Draws MEOW, a sitting black cat, in a 100×100 unit box scaled to fit the canvas. Drawn from
 * the logo (`meow_peek`): a wide round head, broad ears with rounded tips and warm taupe
 * insides that lean outward, a soft curl of fur on top, and very large white eyes whose big
 * pupils look up. Shapes stay bold and few so the cat reads at 40dp as well as at 160dp.
 */
internal fun DrawScope.drawCat(mood: MascotMood, pose: CatPose, colors: CatColors) {
    val unit = size.minDimension / 100f
    val left = (size.width - 100f * unit) / 2f
    val top = (size.height - 100f * unit) / 2f
    withTransform({
        translate(left, top)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        val rimWidth = 1.1f
        val hasRim = colors.rim.alpha > 0f

        // Tail, behind the body.
        rotate(pose.tailAngle * mood.tailAmplitude(), pivot = Offset(66f, 90f)) {
            if (hasRim) drawPath(TailPath, colors.rim, style = Stroke(9f + rimWidth * 2, cap = StrokeCap.Round))
            drawPath(TailPath, CatPalette.Fur, style = Stroke(9f, cap = StrokeCap.Round))
        }

        // Body, breathing gently.
        val breathScale = 1f + 0.018f * pose.breath
        withTransform({ scale(1f, breathScale, pivot = Offset(50f, 100f)) }) {
            // The rim is stroked at twice its width under the fill, so only its outer half
            // shows and no seam inside the shape ever does.
            if (hasRim) drawPath(BodyPath, colors.rim, style = Stroke(rimWidth * 2))
            drawPath(BodyPath, CatPalette.Fur)
            drawPaws()
        }

        // Head and face.
        rotate(pose.headTilt, pivot = Offset(50f, 70f)) {
            // Every outline first, then every fill: the silhouette has an outline only outside.
            // The curl of fur gets no outline: outlined, its strands read as scratches.
            if (hasRim) HeadParts.filter { it !== TuftPath }.forEach { drawPath(it, colors.rim, style = Stroke(rimWidth * 2, join = StrokeJoin.Round)) }
            HeadParts.forEach { drawPath(it, CatPalette.Fur) }
            drawPath(InnerEarLeft, CatPalette.InnerEar)
            drawPath(InnerEarRight, CatPalette.InnerEar)
            drawEyes(mood, pose, colors)
            drawNoseAndMouth(mood)
            drawWhiskers()
            if (mood.blushes) {
                drawOval(colors.accent.copy(alpha = 0.4f), topLeft = Offset(21f, 60f), size = Size(9f, 4.4f))
                drawOval(colors.accent.copy(alpha = 0.4f), topLeft = Offset(70f, 60f), size = Size(9f, 4.4f))
            }
        }

        when (mood) {
            MascotMood.Sleeping -> drawZzz(pose.phase, colors.accent)
            MascotMood.Celebrating -> drawSparkles(pose.phase, colors.accent)
            MascotMood.Surprised -> drawSurpriseMarks(colors.accent)
            MascotMood.Curious -> drawThoughtDots(colors.accent)
            else -> Unit
        }
    }
}

private fun DrawScope.drawPaws() {
    for (cx in floatArrayOf(40.5f, 59.5f)) {
        drawOval(CatPalette.FurShade, topLeft = Offset(cx - 8f, 90.5f), size = Size(16f, 9.5f))
        val toe = 0.8f
        drawLine(CatPalette.Line, Offset(cx - 2.6f, 96f), Offset(cx - 2.6f, 99f), strokeWidth = toe, cap = StrokeCap.Round)
        drawLine(CatPalette.Line, Offset(cx + 2.6f, 96f), Offset(cx + 2.6f, 99f), strokeWidth = toe, cap = StrokeCap.Round)
    }
}

private const val EYE_Y = 50f
private val EyeCentres = floatArrayOf(35.5f, 64.5f)

private fun DrawScope.drawEyes(mood: MascotMood, pose: CatPose, colors: CatColors) {
    for (cx in EyeCentres) {
        when (mood) {
            MascotMood.Happy, MascotMood.Celebrating -> {
                // Smiling eyes: the logo's eye shape pressed into an upward curve.
                val arc = Path().apply {
                    moveTo(cx - 7f, EYE_Y + 2f)
                    quadraticTo(cx, EYE_Y - 7f, cx + 7f, EYE_Y + 2f)
                }
                drawPath(arc, CatPalette.ClosedEye, style = Stroke(3f, cap = StrokeCap.Round))
            }
            MascotMood.Sleeping -> {
                val arc = Path().apply {
                    moveTo(cx - 7f, EYE_Y - 0.5f)
                    quadraticTo(cx, EYE_Y + 5.5f, cx + 7f, EYE_Y - 0.5f)
                }
                drawPath(arc, CatPalette.ClosedEye, style = Stroke(2.6f, cap = StrokeCap.Round))
            }
            else -> drawOpenEye(cx, mood, pose)
        }
    }
}

private fun DrawScope.drawOpenEye(cx: Float, mood: MascotMood, pose: CatPose) {
    val surprised = mood == MascotMood.Surprised
    val rx = if (surprised) 11.2f else 10.4f
    val ry = (if (surprised) 12.2f else 11.2f) * pose.eyeOpen.coerceIn(0.08f, 1f)
    val cy = EYE_Y
    val eyePath = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
    drawPath(eyePath, CatPalette.EyeWhite)

    // Big pupils, as in the logo; their position carries the look.
    val (gx, gy) = mood.gaze
    val pupilR = if (surprised) 4.6f else 6.9f
    val pupil = Offset(cx + gx * (rx - pupilR - 0.8f), cy + gy * (ry - pupilR - 0.6f).coerceAtLeast(0f))
    clipPath(eyePath) {
        drawCircle(CatPalette.Pupil, radius = pupilR, center = pupil)

        // Lids: heavy for sleepy or just waking, slanted for grumpy.
        val lid: Path? = when (mood) {
            MascotMood.Sleepy, MascotMood.Waking -> Path().apply { addRect(Rect(cx - 13f, cy - 14f, cx + 13f, cy - 1f)) }
            MascotMood.Grumpy -> Path().apply {
                val inner = if (cx < 50f) cx + 13f else cx - 13f
                val outer = if (cx < 50f) cx - 13f else cx + 13f
                moveTo(outer, cy - 14f)
                lineTo(inner, cy - 14f)
                lineTo(inner, cy + 1.5f)
                lineTo(outer, cy - 4f)
                close()
            }
            else -> null
        }
        if (lid != null) drawPath(lid, CatPalette.Fur)
    }
    if (mood == MascotMood.Sleepy || mood == MascotMood.Waking) {
        drawLine(CatPalette.Line, Offset(cx - rx + 1f, cy - 1f), Offset(cx + rx - 1f, cy - 1f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawNoseAndMouth(mood: MascotMood) {
    // A small rounded mauve nose, like the logo's.
    val nose = Path().apply {
        moveTo(47.4f, 60.4f)
        quadraticTo(50f, 59.4f, 52.6f, 60.4f)
        quadraticTo(51.2f, 63f, 50f, 63.2f)
        quadraticTo(48.8f, 63f, 47.4f, 60.4f)
        close()
    }
    drawPath(nose, CatPalette.Nose)

    val line = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (mood) {
        MascotMood.Happy, MascotMood.Celebrating -> {
            drawOval(Color(0xFFC77D92), topLeft = Offset(48.3f, 64.6f), size = Size(3.4f, 3.4f))
            val mouth = Path().apply {
                moveTo(45.6f, 63.6f)
                quadraticTo(47.8f, 66.8f, 50f, 64.2f)
                quadraticTo(52.2f, 66.8f, 54.4f, 63.6f)
            }
            drawPath(mouth, CatPalette.Line, style = line)
        }
        MascotMood.Surprised, MascotMood.Waking -> {
            // A small "o": surprise, or a yawn when waking up.
            val h = if (mood == MascotMood.Waking) 4.8f else 4f
            drawOval(Color(0xFF2C2026), topLeft = Offset(48.1f, 64.2f), size = Size(3.8f, h))
            drawOval(CatPalette.Line, topLeft = Offset(48.1f, 64.2f), size = Size(3.8f, h), style = line)
        }
        MascotMood.Grumpy -> {
            val mouth = Path().apply {
                moveTo(46.6f, 66.2f)
                quadraticTo(50f, 63.6f, 53.4f, 66.2f)
            }
            drawPath(mouth, CatPalette.Line, style = line)
        }
        else -> {
            val mouth = Path().apply {
                moveTo(46.6f, 64f)
                quadraticTo(48.3f, 66.2f, 50f, 63.9f)
                quadraticTo(51.7f, 66.2f, 53.4f, 64f)
            }
            drawPath(mouth, CatPalette.Line, style = line)
        }
    }
}

private fun DrawScope.drawWhiskers() {
    // Two short, soft whiskers a side, from the cheeks out past the face.
    val w = 1.1f
    drawLine(CatPalette.Whisker, Offset(25f, 59.5f), Offset(9.5f, 57f), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(CatPalette.Whisker, Offset(25f, 62.5f), Offset(10f, 63.5f), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(CatPalette.Whisker, Offset(75f, 59.5f), Offset(90.5f, 57f), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(CatPalette.Whisker, Offset(75f, 62.5f), Offset(90f, 63.5f), strokeWidth = w, cap = StrokeCap.Round)
}

private fun DrawScope.drawZzz(phase: Float, accent: Color) {
    // Three Zs drifting up and fading, staggered along the phase.
    for (i in 0 until 3) {
        val t = (phase + i / 3f) % 1f
        val s = 4.5f + t * 4f
        val x = 80f + t * 10f
        val y = 24f - t * 20f
        val alpha = when {
            t < 0.15f -> t / 0.15f
            t > 0.75f -> (1f - t) / 0.25f
            else -> 1f
        }
        val z = Path().apply {
            moveTo(x, y)
            lineTo(x + s, y)
            lineTo(x, y + s)
            lineTo(x + s, y + s)
        }
        drawPath(z, accent.copy(alpha = alpha), style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val SparkleSpots = listOf(
    Offset(11f, 30f) to 2.4f,
    Offset(89f, 24f) to 2.8f,
    Offset(6f, 62f) to 1.8f,
    Offset(94f, 60f) to 2.0f,
    Offset(20f, 9f) to 1.6f,
    Offset(80f, 6f) to 2.2f,
)

private fun DrawScope.drawSparkles(phase: Float, accent: Color) {
    SparkleSpots.forEachIndexed { i, (center, r) ->
        val twinkle = 0.6f + 0.4f * sin((phase * 2f * PI + i * 1.3).toFloat())
        drawCircle(accent.copy(alpha = 0.85f), radius = r * twinkle, center = center)
    }
}

/** Three small dots rising beside the head: the cat is thinking. */
private fun DrawScope.drawThoughtDots(accent: Color) {
    drawCircle(accent.copy(alpha = 0.9f), radius = 1.6f, center = Offset(84f, 22f))
    drawCircle(accent.copy(alpha = 0.9f), radius = 2.2f, center = Offset(89f, 15f))
    drawCircle(accent.copy(alpha = 0.9f), radius = 2.9f, center = Offset(94.5f, 7f))
}

private fun DrawScope.drawSurpriseMarks(accent: Color) {
    drawLine(accent, Offset(13f, 14f), Offset(17f, 21f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawLine(accent, Offset(20f, 8.5f), Offset(22.5f, 17f), strokeWidth = 2.2f, cap = StrokeCap.Round)
}

private val MascotMood.gaze: Pair<Float, Float>
    get() = when (this) {
        // At rest the cat looks up, as in the logo.
        MascotMood.Idle -> 0.35f to -0.8f
        MascotMood.Curious -> 0.7f to -0.7f
        MascotMood.Encouraging -> -0.1f to 0.15f
        MascotMood.Grumpy -> -0.6f to 0.1f
        MascotMood.Sleepy, MascotMood.Waking -> 0f to 0.6f
        MascotMood.Surprised -> 0f to 0f
        else -> 0.1f to -0.2f
    }

private val MascotMood.blushes: Boolean
    get() = this == MascotMood.Happy || this == MascotMood.Celebrating || this == MascotMood.Encouraging

private fun MascotMood.tailAmplitude(): Float = when (this) {
    MascotMood.Sleeping, MascotMood.Sleepy, MascotMood.Waking -> 0.35f
    MascotMood.Celebrating, MascotMood.Happy -> 1.4f
    MascotMood.Grumpy -> 1.8f
    else -> 1f
}

internal fun MascotMood.headTilt(): Float = when (this) {
    MascotMood.Curious -> -8f
    MascotMood.Encouraging -> 6f
    MascotMood.Sleeping -> 5f
    MascotMood.Sleepy, MascotMood.Waking -> 3f
    MascotMood.Happy -> -3f
    else -> 0f
}

private val TailPath = Path().apply {
    moveTo(64f, 92f)
    cubicTo(80f, 96f, 92f, 86f, 88f, 70f)
    quadraticTo(86.5f, 63f, 81f, 61f)
}

private val BodyPath = Path().apply { addOval(Rect(27f, 61f, 73f, 100f)) }

/** The logo's ear: broad base, slightly bowed outer edge, rounded tip, curved inner edge. */
private fun ear(mirror: Boolean): Path {
    fun x(v: Float) = if (mirror) 100f - v else v
    return Path().apply {
        moveTo(x(16f), 52f)
        cubicTo(x(12f), 38f, x(12.5f), 22f, x(17.5f), 11f)
        cubicTo(x(19f), 7.5f, x(22.5f), 6.8f, x(25.6f), 9.2f)
        cubicTo(x(33f), 15f, x(40f), 21f, x(46.5f), 28.5f)
        lineTo(x(30f), 42f)
        close()
    }
}

private fun innerEar(mirror: Boolean): Path {
    fun x(v: Float) = if (mirror) 100f - v else v
    return Path().apply {
        moveTo(x(21.5f), 37f)
        cubicTo(x(19.5f), 29f, x(19.5f), 21f, x(22f), 15.5f)
        cubicTo(x(23f), 13.4f, x(25.2f), 13f, x(27f), 14.6f)
        cubicTo(x(32f), 19f, x(36.5f), 23.4f, x(40.5f), 28f)
        cubicTo(x(33f), 30.5f, x(27f), 33.5f, x(21.5f), 37f)
        close()
    }
}

/** The curl of fur on top of the head. */
private val TuftPath = Path().apply {
    moveTo(40.5f, 28.5f)
    quadraticTo(41.8f, 22.5f, 46.2f, 20f)
    quadraticTo(45.2f, 23.6f, 47.4f, 26f)
    quadraticTo(49.4f, 20.2f, 54.2f, 18.6f)
    quadraticTo(52f, 22.6f, 53.6f, 26.2f)
    quadraticTo(56.2f, 23.2f, 59.4f, 23.6f)
    quadraticTo(57.2f, 26.2f, 58.2f, 29f)
    close()
}

/** Head, ears and tuft, drawn as separate overlapping shapes (no path union needed). */
private val HeadParts: List<Path> = listOf(
    ear(false),
    ear(true),
    // Wider than tall, a little fuller at the cheeks.
    Path().apply { addOval(Rect(14f, 26f, 86f, 75f)) },
    TuftPath,
)

private val InnerEarLeft = innerEar(false)

private val InnerEarRight = innerEar(true)
