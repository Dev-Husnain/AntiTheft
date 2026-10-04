package com.anti.theft.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.R
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.ColorFBBF
import com.anti.theft.core.theme.Dimens

@Composable
fun InlineNotice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: NoticeTone = NoticeTone.Info,
    @DrawableRes iconRes: Int = when (tone) {
        NoticeTone.Info -> R.drawable.ic_info
        NoticeTone.Warning, NoticeTone.Error -> R.drawable.ic_warning
    },
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    val accent = when (tone) {
        NoticeTone.Info -> MaterialTheme.colorScheme.primary
        NoticeTone.Warning -> ColorFBBF
        NoticeTone.Error -> MaterialTheme.colorScheme.error
    }
    val actionColor = if (tone == NoticeTone.Warning && MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        MaterialTheme.colorScheme.onSurface
    } else {
        accent
    }
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), shape)
            .border(Dimens.Border, accent.copy(alpha = 0.32f), shape)
            .padding(start = Dimens.SpaceM, top = Dimens.SpaceM, bottom = Dimens.SpaceS, end = Dimens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(Dimens.IconS),
        )
        Column(modifier = Modifier.weight(1f)) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Dimens.SpaceS, bottom = Dimens.SpaceXs),
            )
            actionLabel?.let {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(text = it, style = MaterialTheme.typography.labelLarge, color = actionColor)
                }
            }
        }
        onDismiss?.let {
            IconButton(onClick = it, modifier = Modifier.size(Dimens.MinTouchTarget)) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.cd_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.IconS),
                )
            }
        }
    }
}

@Preview
@Composable
private fun InlineNoticeWarningPreview() {
    AntiTheftTheme(darkTheme = true) {
        InlineNotice(
            title = "Notifications are off",
            message = "Charging alerts can't be shown.",
            tone = NoticeTone.Warning,
            actionLabel = "Turn on",
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun InlineNoticeInfoPreview() {
    AntiTheftTheme(darkTheme = false) {
        InlineNotice(message = "Clap detection paused. Open the app to resume listening.")
    }
}
