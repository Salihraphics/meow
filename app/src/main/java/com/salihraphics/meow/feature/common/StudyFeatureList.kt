package com.salihraphics.meow.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.FeatureRow

/**
 * The core study features. PDF import works; solving and the mistake notebook are marked
 * "Yakında" until they ship.
 *
 * With [onOpenLibrary] (Home), the first row opens the library and shows [libraryCount].
 * Without it (onboarding), the rows only describe what MEOW does.
 */
@Composable
fun StudyFeatureList(
    modifier: Modifier = Modifier,
    onOpenLibrary: (() -> Unit)? = null,
    libraryCount: Int? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onOpenLibrary != null) {
            FeatureRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.home_library_title),
                description = if (libraryCount == null || libraryCount == 0) {
                    stringResource(R.string.home_library_empty)
                } else {
                    stringResource(R.string.home_library_count, libraryCount)
                },
                comingSoon = false,
                onClick = onOpenLibrary,
            )
        } else {
            FeatureRow(
                Icons.Filled.Add,
                stringResource(R.string.feature_import_title),
                stringResource(R.string.feature_import_desc),
                comingSoon = false,
            )
        }
        FeatureRow(Icons.Filled.Create, stringResource(R.string.feature_solve_title), stringResource(R.string.feature_solve_desc))
        FeatureRow(Icons.Filled.Refresh, stringResource(R.string.feature_review_title), stringResource(R.string.feature_review_desc))
    }
}
