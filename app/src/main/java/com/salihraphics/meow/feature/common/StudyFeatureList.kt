package com.salihraphics.meow.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.FeatureRow

/** The three core study features. All are marked "Yakında" until they actually ship. */
@Composable
fun StudyFeatureList(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FeatureRow(Icons.Filled.Add, stringResource(R.string.feature_import_title), stringResource(R.string.feature_import_desc))
        FeatureRow(Icons.Filled.Create, stringResource(R.string.feature_solve_title), stringResource(R.string.feature_solve_desc))
        FeatureRow(Icons.Filled.Refresh, stringResource(R.string.feature_review_title), stringResource(R.string.feature_review_desc))
    }
}
