package com.anti.theft.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.R
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.Color4F7C
import com.anti.theft.core.theme.Dimens
import com.anti.theft.domain.models.Sensitivity

@Composable
fun SensitivitySelector(
    selected: Sensitivity,
    onSelected: (Sensitivity) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            text = stringResource(R.string.sensitivity_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = Sensitivity.entries
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = accent.copy(alpha = 0.18f),
                        activeContentColor = MaterialTheme.colorScheme.onSurface,
                        activeBorderColor = accent.copy(alpha = 0.6f),
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    label = {
                        Text(
                            text = stringResource(
                                when (option) {
                                    Sensitivity.LOW -> R.string.sensitivity_low
                                    Sensitivity.MEDIUM -> R.string.sensitivity_medium
                                    Sensitivity.HIGH -> R.string.sensitivity_high
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131A2E)
@Composable
private fun SensitivitySelectorPreview() {
    AntiTheftTheme(darkTheme = true) {
        SensitivitySelector(selected = Sensitivity.MEDIUM, onSelected = {}, accent = Color4F7C)
    }
}
