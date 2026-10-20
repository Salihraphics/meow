package com.salihraphics.meow.core.mascot

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.BubbleTail
import com.salihraphics.meow.core.designsystem.component.SpeechBubble

/**
 * The cat sitting beside a speech bubble. Tapping the cat plays a reaction; while it plays, the
 * reaction's line replaces [defaultSpeech].
 */
@Composable
fun CompanionCat(
    holder: MascotStateHolder,
    defaultSpeech: String?,
    modifier: Modifier = Modifier,
    catSize: Dp = 128.dp,
) {
    val state = holder.state
    val speech = state.speech?.let { stringResource(it) } ?: defaultSpeech
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        MeowCat(
            mood = state.mood,
            modifier = Modifier.size(catSize),
            onTap = { holder.onEvent(MascotEvent.Tapped) },
            contentDescription = stringResource(R.string.cd_cat_tap),
        )
        Spacer(Modifier.width(4.dp))
        if (speech != null) {
            SpeechBubble(
                text = speech,
                tail = BubbleTail.BottomStart,
                modifier = Modifier.padding(bottom = catSize * 0.55f),
            )
        }
    }
}
