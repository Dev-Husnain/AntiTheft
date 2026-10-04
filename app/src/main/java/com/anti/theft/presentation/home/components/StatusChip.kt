package com.anti.theft.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.ColorFBBF
import com.anti.theft.core.theme.Dimens
import com.anti.theft.presentation.home.StatusTone

/** Small pill with a coloured dot; text stays on-surface for contrast in both themes. */
@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val dot = when (tone) {
        StatusTone.Active -> MaterialTheme.colorScheme.tertiary
        StatusTone.Pending -> ColorFBBF
    }
    Row(
        modifier = modifier
            .background(dot.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.StatusDot),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.StatusDot)
                .background(dot, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview
@Composable
private fun StatusChipPreview() {
    AntiTheftTheme(darkTheme = true) {
        StatusChip(label = "Armed", tone = StatusTone.Active)
    }
}
