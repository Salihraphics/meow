package com.salihraphics.meow.core.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.theme.LocalIsDarkTheme
import com.salihraphics.meow.core.designsystem.theme.LocalMeowMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * MEOW the cat. Ambient motion (blinks, tail, breathing) only runs when [LocalMeowMotion]
 * allows it, and animated values are read in the draw phase so they never recompose the screen.
 */
@Composable
fun MeowCat(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    contentDescription: String = stringResource(R.string.cd_mascot),
) {
    val animate = LocalMeowMotion.current.ambientAnimations
    val colors = CatColors(
        rim = if (LocalIsDarkTheme.current) Color(0xFF34313C) else Color.Transparent,
        accent = MaterialTheme.colorScheme.primary,
    )

    val headTilt = animateFloatAsState(
        targetValue = mood.headTilt(),
        animationSpec = if (animate) spring(dampingRatio = 0.55f, stiffness = 220f) else tween(0),
        label = "headTilt",
    )

    val ambient = if (animate) rememberAmbientMotion(mood) else rememberStaticMotion()

    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val tapModifier = if (onTap != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClickLabel = contentDescription,
        ) {
            onTap()
            if (animate) {
                scope.launch {
                    pop.animateTo(0.92f, tween(70))
                    pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription }
            .then(tapModifier)
            .graphicsLayer {
                val p = pop.value
                scaleX = p
                scaleY = p
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -ambient.bounce.value * size.height * 0.035f
            },
    ) {
        drawCat(
            mood = mood,
            pose = CatPose(
                eyeOpen = ambient.blink.value,
                tailAngle = ambient.tail.value,
                headTilt = headTilt.value,
                breath = ambient.breath.value,
                phase = ambient.phase.value,
            ),
            colors = colors,
        )
    }
}

private class AmbientMotion(
    val blink: State<Float>,
    val tail: State<Float>,
    val breath: State<Float>,
    val phase: State<Float>,
    val bounce: State<Float>,
)

@Composable
private fun rememberStaticMotion(): AmbientMotion = remember {
    val zero = mutableFloatStateOf(0f)
    AmbientMotion(blink = mutableFloatStateOf(1f), tail = zero, breath = zero, phase = mutableFloatStateOf(0.35f), bounce = zero)
}

@Composable
private fun rememberAmbientMotion(mood: MascotMood): AmbientMotion {
    val transition = rememberInfiniteTransition(label = "meowAmbient")
    val blink = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4_300
                1f at 3_950
                0.08f at 4_040
                1f at 4_140
            },
        ),
        label = "blink",
    )
    val tail = transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(1_700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tail",
    )
    val breath = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == MascotMood.Sleeping) 2_800 else 2_200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing)),
        label = "phase",
    )
    val celebrating = mood == MascotMood.Celebrating
    val bounce = remember(phase, celebrating) {
        object : State<Float> {
            override val value: Float
                get() = if (celebrating) abs(sin(phase.value * 2f * Math.PI.toFloat() * 2f)) else 0f
        }
    }
    return remember(blink, tail, breath, phase, bounce) { AmbientMotion(blink, tail, breath, phase, bounce) }
}
