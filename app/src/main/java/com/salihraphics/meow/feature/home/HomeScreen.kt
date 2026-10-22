package com.salihraphics.meow.feature.home

import com.salihraphics.meow.core.designsystem.theme.cardBorder
import com.salihraphics.meow.core.designsystem.theme.cardColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWidths
import com.salihraphics.meow.core.designsystem.component.MeowTextButton
import com.salihraphics.meow.core.mascot.CompanionCat
import com.salihraphics.meow.core.mascot.MascotState
import com.salihraphics.meow.core.mascot.rememberMascotState
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.feature.common.labelRes
import com.salihraphics.meow.feature.common.StudyFeatureList

/**
 * The main screen after onboarding. Study features are listed honestly as "coming soon"
 * until they are implemented; nothing here pretends to be a working button.
 */
@Composable
fun HomeScreen(
    profile: StudyProfile,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    libraryCount: Int?,
    modifier: Modifier = Modifier,
) {
    val cat = rememberMascotState(MascotState())
    val twoColumns = LocalMeowWindow.current.useTwoColumns

    val companionAndProfile = @Composable {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = cardColor,
            border = cardBorder,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompanionCat(
                holder = cat,
                defaultSpeech = stringResource(R.string.home_subtitle),
                modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                catSize = if (twoColumns) 156.dp else 132.dp,
            )
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle(stringResource(R.string.home_profile_heading))
        ProfileSummary(profile, onEdit = onOpenSettings)
    }
    val workspace = @Composable {
        SectionTitle(stringResource(R.string.home_workspace_heading))
        Text(
            stringResource(R.string.home_workspace_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        StudyFeatureList(onOpenLibrary = onOpenLibrary, libraryCount = libraryCount)
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (twoColumns) MeowWidths.Wide else 640.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (twoColumns) 32.dp else 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.displayName?.let { stringResource(R.string.home_greeting_named, it) }
                        ?: stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_open_settings))
                }
            }
            Spacer(Modifier.height(20.dp))
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f)) { companionAndProfile() }
                    Column(Modifier.weight(1f)) { workspace() }
                }
            } else {
                companionAndProfile()
                Spacer(Modifier.height(28.dp))
                workspace()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSummary(profile: StudyProfile, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = cardColor, border = cardBorder, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            val tags = buildList {
                profile.exam?.let { add(stringResource(it.labelRes)) }
                profile.level?.let { add(stringResource(it.labelRes)) }
                profile.sessionLength?.let { add(stringResource(R.string.home_profile_session, it.minutes)) }
                profile.subjects.forEach { add(stringResource(it.labelRes)) }
            }
            if (tags.isEmpty()) {
                Text(
                    stringResource(R.string.home_profile_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    tags.forEach { tag ->
                        Surface(shape = CircleShape, color = scheme.primaryContainer) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                MeowTextButton(stringResource(R.string.home_open_settings), onClick = onEdit)
            }
        }
    }
}
