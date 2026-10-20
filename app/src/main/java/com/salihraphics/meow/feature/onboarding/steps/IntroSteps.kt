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
import androidx.compose.foundation.layout.sizeIn
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
import com.salihraphics.meow.core.designsystem.adaptive.CenteredContent
import com.salihraphics.meow.core.designsystem.adaptive.CenteredScrollColumn
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWidths
import com.salihraphics.meow.core.designsystem.adaptive.TwoPaneRow
import com.salihraphics.meow.core.designsystem.adaptive.WidthClass
import com.salihraphics.meow.core.designsystem.component.MeowButtonStyle
import com.salihraphics.meow.core.designsystem.component.MeowPrimaryButton
import com.salihraphics.meow.core.designsystem.theme.BrandPurple
import com.salihraphics.meow.core.designsystem.theme.LightBarIconsWhileVisible
import com.salihraphics.meow.core.designsystem.theme.LocalIsDarkTheme
import com.salihraphics.meow.core.designsystem.theme.LocalMeowMotion
import com.salihraphics.meow.core.mascot.MascotEvent
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MascotState
import com.salihraphics.meow.core.mascot.MascotStateHolder
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.core.mascot.rememberMascotState
import com.salihraphics.meow.feature.common.StudyFeatureList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Brief brand moment on first launch. Advances by itself, or immediately on tap. */
@Composable
internal fun WelcomeStep(onContinue: () -> Unit, autoAdvanceMillis: Long = 2_200L) {
    val animate = LocalMeowMotion.current.ambientAnimations
    val twoPanes = LocalMeowWindow.current.useTwoPanes
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

    val wordmark = @Composable { modifier: Modifier, align: Alignment.Horizontal, textAlign: TextAlign ->
        Column(modifier.graphicsLayer { alpha = textAlpha.value }, horizontalAlignment = align) {
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
                textAlign = textAlign,
            )
        }
    }
    // The logo artwork: its purple background is the same colour as this screen.
    val peekingCat = @Composable { modifier: Modifier ->
        Image(
            painter = painterResource(R.drawable.meow_peek),
            contentDescription = stringResource(R.string.cd_peeking_cat),
            contentScale = ContentScale.Fit,
            modifier = modifier
                .aspectRatio(1f)
                .graphicsLayer {
                    translationY = rise.value * size.height * 0.45f
                    alpha = 1f - rise.value * 0.6f
                },
        )
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
        if (twoPanes) {
            // Landscape: wordmark on the left, the cat peeking up from the bottom edge on the right.
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 48.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    wordmark(Modifier, Alignment.Start, TextAlign.Start)
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    peekingCat(Modifier.fillMaxHeight(0.9f))
                }
            }
        } else {
            wordmark(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 96.dp, start = 24.dp, end = 24.dp),
                Alignment.CenterHorizontally,
                TextAlign.Center,
            )
            peekingCat(Modifier.align(Alignment.BottomCenter).widthIn(max = 480.dp).fillMaxWidth())
        }
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
    IntroLayout(
        cat = { modifier -> TappableCat(cat, modifier) },
        text = {
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
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.meet_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        action = {
            MeowPrimaryButton(text = stringResource(R.string.meet_cta), onClick = onContinue, style = MeowButtonStyle.Brand)
        },
    )
}

@Composable
internal fun ReadyStep(displayName: String?, isSaving: Boolean, onFinish: () -> Unit) {
    val cat = rememberMascotState(MascotState(MascotMood.Celebrating))
    val twoPanes = LocalMeowWindow.current.useTwoPanes
    IntroLayout(
        cat = { modifier -> TappableCat(cat, modifier) },
        textAlign = if (twoPanes) TextAlign.Start else TextAlign.Center,
        text = {
            Text(
                if (displayName == null) stringResource(R.string.ready_title)
                else stringResource(R.string.ready_title_named, displayName),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = if (twoPanes) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.ready_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (twoPanes) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.ready_next_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            StudyFeatureList()
        },
        action = {
            MeowPrimaryButton(
                text = stringResource(R.string.ready_cta),
                onClick = onFinish,
                enabled = !isSaving,
                style = MeowButtonStyle.Brand,
            )
        },
    )
}

@Composable
private fun TappableCat(holder: MascotStateHolder, modifier: Modifier) {
    MeowCat(
        mood = holder.state.mood,
        onTap = { holder.onEvent(MascotEvent.Tapped) },
        contentDescription = stringResource(R.string.cd_cat_tap),
        modifier = modifier,
    )
}

/**
 * Cat + text + action. Phones stack them in one readable column; wide windows give the cat its
 * own pane so it can be large without pushing the text off screen.
 */
@Composable
private fun IntroLayout(
    cat: @Composable (Modifier) -> Unit,
    text: @Composable () -> Unit,
    action: @Composable () -> Unit,
    textAlign: TextAlign = TextAlign.Start,
) {
    val window = LocalMeowWindow.current
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (window.useTwoPanes) {
            TwoPaneRow(Modifier.padding(horizontal = 32.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    cat(Modifier.sizeIn(maxWidth = 360.dp, maxHeight = 360.dp).fillMaxSize(0.8f))
                }
                if (window.keepActionsNearContent) {
                    CenteredScrollColumn(Modifier.weight(1f).fillMaxHeight().widthIn(max = MeowWidths.Readable)) {
                        text()
                        Spacer(Modifier.height(32.dp))
                        action()
                    }
                } else {
                    Column(Modifier.weight(1f).fillMaxHeight().widthIn(max = MeowWidths.Readable)) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Spacer(Modifier.height(24.dp))
                            text()
                            Spacer(Modifier.height(24.dp))
                        }
                        Box(Modifier.padding(top = 8.dp, bottom = 16.dp)) { action() }
                    }
                }
            }
        } else {
            val catSize = if (window.width == WidthClass.Compact) 210.dp else 280.dp
            val centered = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
            if (window.keepActionsNearContent) {
                CenteredContent(MeowWidths.Readable, Modifier.fillMaxHeight()) {
                    CenteredScrollColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = centered) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { cat(Modifier.size(catSize)) }
                        Spacer(Modifier.height(32.dp))
                        text()
                        Spacer(Modifier.height(32.dp))
                        action()
                        Spacer(Modifier.height(16.dp))
                    }
                }
                return@Box
            }
            CenteredContent(MeowWidths.Readable, Modifier.fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = centered,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            cat(Modifier.size(catSize))
                        }
                        Spacer(Modifier.height(28.dp))
                        text()
                        Spacer(Modifier.height(24.dp))
                    }
                    Box(Modifier.padding(top = 8.dp, bottom = 16.dp)) { action() }
                }
            }
        }
    }
}
