package com.salihraphics.meow.core.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
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
 * Draws MEOW, a sitting black cat, in a 100×100 unit box scaled to fit the canvas.
 * Geometry is hand-tuned to echo the logo: big round head, tall ears with mauve insides,
 * a small tuft, large white eyes.
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
            drawPath(BodyPath, CatPalette.Fur)
            if (hasRim) drawPath(BodyPath, colors.rim, style = Stroke(rimWidth))
            drawPaws()
        }

        // Head and face.
        rotate(pose.headTilt, pivot = Offset(50f, 70f)) {
            drawPath(HeadPath, CatPalette.Fur)
            if (hasRim) drawPath(HeadPath, colors.rim, style = Stroke(rimWidth, join = StrokeJoin.Round))
            drawPath(InnerEarLeft, CatPalette.InnerEar)
            drawPath(InnerEarRight, CatPalette.InnerEar)
            drawEyes(mood, pose, colors)
            drawNoseAndMouth(mood)
            drawWhiskers()
            if (mood.blushes) {
                drawOval(colors.accent.copy(alpha = 0.38f), topLeft = Offset(25f, 55.5f), size = Size(9f, 4.6f))
                drawOval(colors.accent.copy(alpha = 0.38f), topLeft = Offset(66f, 55.5f), size = Size(9f, 4.6f))
            }
        }

        when (mood) {
            MascotMood.Sleeping -> drawZzz(pose.phase, colors.accent)
            MascotMood.Celebrating -> drawSparkles(pose.phase, colors.accent)
            MascotMood.Surprised -> drawSurpriseMarks(colors.accent)
            else -> Unit
        }
    }
}

private fun DrawScope.drawPaws() {
    for (cx in floatArrayOf(41f, 59f)) {
        drawOval(CatPalette.FurShade, topLeft = Offset(cx - 7.5f, 91f), size = Size(15f, 9f))
        val toe = Stroke(0.7f, cap = StrokeCap.Round)
        drawLine(CatPalette.Line, Offset(cx - 2.4f, 96.5f), Offset(cx - 2.4f, 99f), strokeWidth = toe.width, cap = toe.cap)
        drawLine(CatPalette.Line, Offset(cx + 2.4f, 96.5f), Offset(cx + 2.4f, 99f), strokeWidth = toe.width, cap = toe.cap)
    }
}

private fun DrawScope.drawEyes(mood: MascotMood, pose: CatPose, colors: CatColors) {
    for (cx in floatArrayOf(37.5f, 62.5f)) {
        when (mood) {
            MascotMood.Happy, MascotMood.Celebrating -> {
                val arc = Path().apply {
                    moveTo(cx - 6f, 49f)
                    quadraticTo(cx, 41.5f, cx + 6f, 49f)
                }
                drawPath(arc, CatPalette.ClosedEye, style = Stroke(2.4f, cap = StrokeCap.Round))
            }
            MascotMood.Sleeping -> {
                val arc = Path().apply {
                    moveTo(cx - 6f, 46.5f)
                    quadraticTo(cx, 52.5f, cx + 6f, 46.5f)
                }
                drawPath(arc, CatPalette.ClosedEye, style = Stroke(2.2f, cap = StrokeCap.Round))
            }
            else -> drawOpenEye(cx, mood, pose)
        }
    }
}

private fun DrawScope.drawOpenEye(cx: Float, mood: MascotMood, pose: CatPose) {
    val surprised = mood == MascotMood.Surprised
    val rx = if (surprised) 9.4f else 8.6f
    val ry = (if (surprised) 10.6f else 9.4f) * pose.eyeOpen.coerceIn(0.08f, 1f)
    val cy = 47f
    val eyeRect = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
    val eyePath = Path().apply { addOval(eyeRect) }
    drawPath(eyePath, CatPalette.EyeWhite)

    val (gx, gy) = mood.gaze
    val pupilR = if (surprised) 3.6f else 5.2f
    val pupil = Offset(cx + gx * (rx - pupilR - 0.6f), cy + gy * (ry - pupilR - 0.4f).coerceAtLeast(0f))
    clipPath(eyePath) {
        drawCircle(CatPalette.Pupil, radius = pupilR, center = pupil)
        drawCircle(CatPalette.EyeWhite, radius = 1.5f, center = pupil + Offset(-1.6f, -1.9f))
        drawCircle(CatPalette.EyeWhite, radius = 0.7f, center = pupil + Offset(1.5f, 1.4f))

        // Eyelids for sleepy and grumpy looks.
        val lid: Path? = when (mood) {
            MascotMood.Sleepy -> Path().apply { addRect(Rect(cx - 11f, cy - 13f, cx + 11f, cy - 0.5f)) }
            MascotMood.Grumpy -> Path().apply {
                // Inner corner sits lower than the outer one.
                val inner = if (cx < 50f) cx + 11f else cx - 11f
                val outer = if (cx < 50f) cx - 11f else cx + 11f
                moveTo(outer, cy - 13f)
                lineTo(inner, cy - 13f)
                lineTo(inner, cy + 1.5f)
                lineTo(outer, cy - 3.5f)
                close()
            }
            else -> null
        }
        if (lid != null) drawPath(lid, CatPalette.Fur)
    }
}

private fun DrawScope.drawNoseAndMouth(mood: MascotMood) {
    val nose = Path().apply {
        moveTo(47.4f, 56f)
        quadraticTo(50f, 55.2f, 52.6f, 56f)
        lineTo(50f, 59f)
        close()
    }
    drawPath(nose, CatPalette.Nose)

    val line = Stroke(1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (mood) {
        MascotMood.Happy, MascotMood.Celebrating -> {
            drawOval(Color(0xFFC77D92), topLeft = Offset(48.3f, 60.6f), size = Size(3.4f, 3.6f))
            val mouth = Path().apply {
                moveTo(45.4f, 59.6f)
                quadraticTo(47.7f, 63f, 50f, 60.2f)
                quadraticTo(52.3f, 63f, 54.6f, 59.6f)
            }
            drawPath(mouth, CatPalette.Line, style = line)
        }
        MascotMood.Surprised -> {
            drawOval(Color(0xFF2C2026), topLeft = Offset(48.2f, 60.2f), size = Size(3.6f, 4.4f))
            drawOval(CatPalette.Line, topLeft = Offset(48.2f, 60.2f), size = Size(3.6f, 4.4f), style = line)
        }
        MascotMood.Grumpy -> {
            val mouth = Path().apply {
                moveTo(46.5f, 62.4f)
                quadraticTo(50f, 59.6f, 53.5f, 62.4f)
            }
            drawPath(mouth, CatPalette.Line, style = line)
        }
        else -> {
            val mouth = Path().apply {
                moveTo(46.2f, 60.2f)
                quadraticTo(48.1f, 62.7f, 50f, 60f)
                quadraticTo(51.9f, 62.7f, 53.8f, 60.2f)
            }
            drawPath(mouth, CatPalette.Line, style = line)
        }
    }
}

private fun DrawScope.drawWhiskers() {
    val w = 0.8f
    drawLine(CatPalette.Line, Offset(28f, 57f), Offset(13.5f, 55f), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(CatPalette.Line, Offset(28f, 59.8f), Offset(13f, 61f), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(CatPalette.Line, Offset(72f, 57f), Offset(86.5f, 55f), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(CatPalette.Line, Offset(72f, 59.8f), Offset(87f, 61f), strokeWidth = w, cap = StrokeCap.Round)
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

private fun DrawScope.drawSurpriseMarks(accent: Color) {
    drawLine(accent, Offset(13f, 14f), Offset(17f, 21f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawLine(accent, Offset(20f, 8.5f), Offset(22.5f, 17f), strokeWidth = 2.2f, cap = StrokeCap.Round)
}

private val MascotMood.gaze: Pair<Float, Float>
    get() = when (this) {
        MascotMood.Curious -> 0.75f to -0.7f
        MascotMood.Encouraging -> -0.25f to 0.4f
        MascotMood.Grumpy -> -0.55f to 0.1f
        MascotMood.Sleepy -> 0f to 0.6f
        MascotMood.Surprised -> 0f to 0f
        else -> 0.15f to 0.1f
    }

private val MascotMood.blushes: Boolean
    get() = this == MascotMood.Happy || this == MascotMood.Celebrating || this == MascotMood.Encouraging

private fun MascotMood.tailAmplitude(): Float = when (this) {
    MascotMood.Sleeping, MascotMood.Sleepy -> 0.35f
    MascotMood.Celebrating, MascotMood.Happy -> 1.4f
    MascotMood.Grumpy -> 1.8f
    else -> 1f
}

internal fun MascotMood.headTilt(): Float = when (this) {
    MascotMood.Curious -> -8f
    MascotMood.Encouraging -> 6f
    MascotMood.Sleeping -> 5f
    MascotMood.Sleepy -> 3f
    MascotMood.Happy -> -3f
    else -> 0f
}

private val TailPath = Path().apply {
    moveTo(64f, 92f)
    cubicTo(80f, 96f, 92f, 86f, 88f, 70f)
    quadraticTo(86.5f, 63f, 81f, 61f)
}

private val BodyPath = Path().apply { addOval(Rect(26f, 58f, 74f, 100f)) }

private val HeadPath: Path = run {
    val head = Path().apply { addOval(Rect(16.5f, 22f, 83.5f, 72f)) }
    val earLeft = Path().apply {
        moveTo(20f, 42f)
        lineTo(19.6f, 14f)
        quadraticTo(20f, 6.4f, 26.8f, 9.6f)
        lineTo(45.5f, 25.5f)
        close()
    }
    val earRight = Path().apply {
        moveTo(80f, 42f)
        lineTo(80.4f, 14f)
        quadraticTo(80f, 6.4f, 73.2f, 9.6f)
        lineTo(54.5f, 25.5f)
        close()
    }
    val tuft = Path().apply {
        moveTo(44.5f, 24f)
        lineTo(47.5f, 18.8f)
        lineTo(50.2f, 22.6f)
        lineTo(53.2f, 19.8f)
        lineTo(55.8f, 24.2f)
        close()
    }
    listOf(earLeft, earRight, tuft).fold(head) { acc, part -> Path.combine(PathOperation.Union, acc, part) }
}

private val InnerEarLeft = Path().apply {
    moveTo(24.6f, 33f)
    lineTo(24.4f, 16.6f)
    quadraticTo(24.8f, 12.6f, 28.4f, 14.6f)
    lineTo(40f, 25f)
    close()
}

private val InnerEarRight = Path().apply {
    moveTo(75.4f, 33f)
    lineTo(75.6f, 16.6f)
    quadraticTo(75.2f, 12.6f, 71.6f, 14.6f)
    lineTo(60f, 25f)
    close()
}
