package com.anti.theft.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.R
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.Dimens
import com.anti.theft.core.ui.InlineNotice
import com.anti.theft.core.ui.NoticeTone
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.models.Sensitivity
import com.anti.theft.presentation.home.FeatureNoticeUiModel
import com.anti.theft.presentation.home.FeatureStatusUiModel
import com.anti.theft.presentation.home.FeatureUiModel
import com.anti.theft.presentation.home.NoticeAction
import com.anti.theft.presentation.home.StatusTone

/** One guard: icon, title, live status, switch, description, sensitivity and any notice. */
@Composable
fun FeatureToggleCard(
    model: FeatureUiModel,
    onCheckedChange: (Boolean) -> Unit,
    onSensitivityChange: (Sensitivity) -> Unit,
    onNoticeAction: (NoticeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = model.feature.accentColor()
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), shape)
            .border(
                Dimens.Border,
                if (model.checked) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                shape,
            )
            .padding(Dimens.SpaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = model.checked,
                    enabled = model.available,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .alpha(if (model.available) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.IconBadge)
                    .background(accent.copy(alpha = 0.16f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(model.iconRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(Dimens.IconM),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) {
                Text(
                    text = stringResource(model.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                model.status?.let { status ->
                    StatusChip(
                        label = status.labelArg?.let { stringResource(status.labelRes, it) }
                            ?: stringResource(status.labelRes),
                        tone = status.tone,
                    )
                }
            }
            // The whole row is the toggle target; the switch only shows the state.
            Switch(
                checked = model.checked,
                onCheckedChange = null,
                enabled = model.available,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                ),
            )
        }
        Text(
            text = stringResource(model.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(
            visible = model.checked && model.sensitivity != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            model.sensitivity?.let {
                SensitivitySelector(
                    selected = it,
                    onSelected = onSensitivityChange,
                    accent = accent,
                    modifier = Modifier.padding(top = Dimens.SpaceXs),
                )
            }
        }
        model.notice?.let { notice ->
            InlineNotice(
                message = stringResource(notice.messageRes),
                tone = notice.tone,
                actionLabel = notice.actionRes?.let { stringResource(it) },
                onAction = { notice.action?.let(onNoticeAction) },
            )
        }
    }
}

@Preview
@Composable
private fun FeatureToggleCardOnPreview() {
    AntiTheftTheme(darkTheme = true) {
        FeatureToggleCard(
            model = FeatureUiModel(
                feature = ProtectionFeature.MOTION,
                iconRes = R.drawable.ic_vibration,
                titleRes = R.string.feature_motion_title,
                descriptionRes = R.string.feature_motion_body,
                checked = true,
                available = true,
                status = FeatureStatusUiModel(R.string.status_arming, 3, StatusTone.Pending),
                sensitivity = Sensitivity.MEDIUM,
                notice = FeatureNoticeUiModel(R.string.notice_motion_battery, NoticeTone.Info),
            ),
            onCheckedChange = {},
            onSensitivityChange = {},
            onNoticeAction = {},
        )
    }
}

@Preview
@Composable
private fun FeatureToggleCardBlockedPreview() {
    AntiTheftTheme(darkTheme = false) {
        FeatureToggleCard(
            model = FeatureUiModel(
                feature = ProtectionFeature.CLAP,
                iconRes = R.drawable.ic_mic,
                titleRes = R.string.feature_clap_title,
                descriptionRes = R.string.feature_clap_body,
                checked = false,
                available = true,
                sensitivity = Sensitivity.MEDIUM,
                notice = FeatureNoticeUiModel(
                    messageRes = R.string.notice_mic_blocked,
                    tone = NoticeTone.Warning,
                    actionRes = R.string.action_open_settings,
                    action = NoticeAction.FIX_PERMISSION,
                ),
            ),
            onCheckedChange = {},
            onSensitivityChange = {},
            onNoticeAction = {},
        )
    }
}
