package com.anti.theft.presentation.alarm.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.R
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.ColorF5F7
import com.anti.theft.core.theme.ColorFFFF
import com.anti.theft.core.theme.Dimens
import com.anti.theft.domain.models.AlarmTrigger

/** Alarm icon, title and what triggered it. Text colours are fixed for the red backdrop. */
@Composable
fun AlarmHeadline(
    trigger: AlarmTrigger?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.IconBadgeLarge)
                .background(ColorFFFF.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_crisis_alert),
                contentDescription = null,
                tint = ColorFFFF,
                modifier = Modifier.size(Dimens.Space3xl),
            )
        }
        Text(
            text = stringResource(R.string.alarm_title),
            style = MaterialTheme.typography.displayLarge,
            color = ColorFFFF,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Assertive
            },
        )
        trigger?.let {
            Text(
                text = stringResource(
                    when (it) {
                        AlarmTrigger.MOTION -> R.string.alarm_reason_motion
                        AlarmTrigger.CLAP -> R.string.alarm_reason_clap
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = ColorFFFF,
                modifier = Modifier
                    .background(ColorFFFF.copy(alpha = 0.16f), CircleShape)
                    .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceXs),
            )
            Text(
                text = stringResource(
                    when (it) {
                        AlarmTrigger.MOTION -> R.string.alarm_body_motion
                        AlarmTrigger.CLAP -> R.string.alarm_body_clap
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = ColorF5F7.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFB3123A)
@Composable
private fun AlarmHeadlinePreview() {
    AntiTheftTheme {
        AlarmHeadline(trigger = AlarmTrigger.MOTION)
    }
}
