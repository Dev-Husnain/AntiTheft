package com.anti.theft.presentation.alarm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.R
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.ColorB312
import com.anti.theft.core.theme.ColorFFFF
import com.anti.theft.core.theme.Dimens
import com.anti.theft.core.ui.PulseRings

@Composable
fun StopAlarmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(Dimens.AlarmStopButton * 1.5f),
        contentAlignment = Alignment.Center,
    ) {
        PulseRings(
            color = ColorFFFF,
            periodMillis = 1400,
            modifier = Modifier.size(Dimens.AlarmStopButton * 1.5f),
        )
        Button(
            onClick = onClick,
            modifier = Modifier.size(Dimens.AlarmStopButton * 0.8f),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = ColorFFFF, contentColor = ColorB312),
            contentPadding = PaddingValues(Dimens.SpaceM),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.Space3xl),
                )
                Text(
                    text = stringResource(R.string.alarm_stop),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFB3123A)
@Composable
private fun StopAlarmButtonPreview() {
    AntiTheftTheme {
        StopAlarmButton(onClick = {})
    }
}
