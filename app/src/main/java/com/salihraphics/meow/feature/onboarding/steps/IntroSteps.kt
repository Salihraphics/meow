package com.salihraphics.meow.feature.onboarding.steps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.MeowButtonStyle
import com.salihraphics.meow.core.designsystem.component.MeowPrimaryButton
import com.salihraphics.meow.core.designsystem.theme.BrandPurple
import com.salihraphics.meow.core.designsystem.theme.LightBarIconsWhileVisible
import com.salihraphics.meow.core.designsystem.theme.LocalIsDarkTheme
import com.salihraphics.meow.core.designsystem.theme.LocalMeowMotion
import com.salihraphics.meow.core.mascot.MascotEvent
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MascotState
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.core.mascot.rememberMascotState
import com.salihraphics.meow.feature.common.StudyFeatureList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Brief brand moment on first launch. Advances by itself, or immediately on tap. */
@Composable
internal fun WelcomeStep(onContinue: () -> Unit, autoAdvanceMillis: Long = 2_200L) {
    val animate = LocalMeowMotion.current.ambientAnimations
    val latestContinue = rememberUpdatedState(onContinue)
    val rise = remember { Animatable(if (animate) 1f else 0f) }
    val textAlpha = remember { Animatable(if (animate) 0f else 1f) }

    LightBarIconsWhileVisible(restoreDarkIcons = !LocalIsDarkTheme.current)

    LaunchedEffect(Unit) {
        if (animate) {
            launch { textAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            launch { rise.animateTo(0f, tween(750, delayMillis = 150, easing = FastOutSlowInEasing)) }
        }
        delay(autoAdvanceMillis)
        latestContinue.value()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BrandPurple)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.action_continue),
            ) { latestContinue.value() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 96.dp, start = 24.dp, end = 24.dp)
                .graphicsLayer { alpha = textAlpha.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
            )
        }
        // The logo artwork: its purple background is the same colour as this screen.
        Image(
            painter = painterResource(R.drawable.meow_peek),
            contentDescription = stringResource(R.string.cd_peeking_cat),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    translationY = rise.value * size.height * 0.45f
                    alpha = 1f - rise.value * 0.6f
                },
        )
    }
}

@Composable
internal fun MeetStep(onContinue: () -> Unit) {
    val animate = LocalMeowMotion.current.ambientAnimations
    // The cat is napping when we arrive, then wakes up to say hello.
    val cat = rememberMascotState(MascotState(if (animate) MascotMood.Sleeping else MascotMood.Idle))
    LaunchedEffect(Unit) {
        if (animate) {
            delay(1_300)
            cat.rest(MascotState(MascotMood.Idle))
            cat.onEvent(MascotEvent.Greet)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MeowCat(
                    mood = cat.state.mood,
                    onTap = { cat.onEvent(MascotEvent.Tapped) },
                    contentDescription = stringResource(R.string.cd_cat_tap),
                    modifier = Modifier.size(220.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.meet_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.meet_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.meet_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        MeowPrimaryButton(
            text = stringResource(R.string.meet_cta),
            onClick = onContinue,
            style = MeowButtonStyle.Brand,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

@Composable
internal fun ReadyStep(displayName: String?, isSaving: Boolean, onFinish: () -> Unit) {
    val cat = rememberMascotState(MascotState(MascotMood.Celebrating))
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            MeowCat(
                mood = cat.state.mood,
                onTap = { cat.onEvent(MascotEvent.Tapped) },
                contentDescription = stringResource(R.string.cd_cat_tap),
                modifier = Modifier.size(196.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                if (displayName == null) stringResource(R.string.ready_title)
                else stringResource(R.string.ready_title_named, displayName),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.ready_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.ready_next_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            StudyFeatureList()
            Spacer(Modifier.height(16.dp))
        }
        MeowPrimaryButton(
            text = stringResource(R.string.ready_cta),
            onClick = onFinish,
            enabled = !isSaving,
            style = MeowButtonStyle.Brand,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }
}
