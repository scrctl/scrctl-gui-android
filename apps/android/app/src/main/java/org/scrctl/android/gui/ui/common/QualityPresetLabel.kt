package org.scrctl.android.gui.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.scrctl.android.gui.R
import org.scrctl.android.gui.data.QualityPreset

@Composable
fun QualityPreset.label(): String = stringResource(
    when (this) {
        QualityPreset.Low -> R.string.quality_low
        QualityPreset.Balanced -> R.string.quality_balanced
        QualityPreset.High -> R.string.quality_high
    },
)

@Composable
fun QualityPreset.summary(): String = stringResource(
    when (this) {
        QualityPreset.Low -> R.string.quality_low_summary
        QualityPreset.Balanced -> R.string.quality_balanced_summary
        QualityPreset.High -> R.string.quality_high_summary
    },
)
