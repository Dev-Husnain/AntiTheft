package com.anti.theft.presentation.home.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.min
import com.anti.theft.R
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.ColorFBBF
import com.anti.theft.core.theme.ColorFFFF
import com.anti.theft.core.theme.Dimens
import com.anti.theft.core.ui.PrimaryButton
import com.anti.theft.core.ui.PulseRings
import com.anti.theft.domain.models.AlarmTrigger
import com.anti.theft.presentation.home.HeroUiModel

/** The animated shield that summarises protection status, with its call to action. */
@Composable
fun ProtectionStatusHero(
    hero: HeroUiModel,
    onStopAlarm: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetAccent = when (hero) {
        HeroUiModel.Off -> MaterialTheme.colorScheme.onSurfaceVariant
        is HeroUiModel.Protected -> MaterialTheme.colorScheme.tertiary
        is HeroUiModel.Arming, HeroUiModel.Paused -> ColorFBBF
        is HeroUiModel.Ringing -> MaterialTheme.colorScheme.error
    }
    val accent by animateColorAsState(targetAccent, tween(500), label = "heroAccent")
    val title = when (hero) {
        HeroUiModel.Off -> stringResource(R.string.hero_off_title)
        is HeroUiModel.Protected -> stringResource(R.string.hero_protected_title)
        is HeroUiModel.Arming -> stringResource(R.string.hero_arming_title)
        HeroUiModel.Paused -> stringResource(R.string.hero_paused_title)
        is HeroUiModel.Ringing -> stringResource(R.string.hero_ringing_title)
    }
    val body = when (hero) {
        HeroUiModel.Off -> stringResource(R.string.hero_off_body)
        is HeroUiModel.Protected ->
            pluralStringResource(R.plurals.hero_protected_body, hero.activeGuards, hero.activeGuards)
        is HeroUiModel.Arming -> stringResource(R.string.hero_arming_body, hero.secondsLeft)
        HeroUiModel.Paused -> stringResource(R.string.hero_paused_body)
        is HeroUiModel.Ringing -> stringResource(
            when (hero.trigger) {
                AlarmTrigger.MOTION -> R.string.alarm_reason_motion
                AlarmTrigger.CLAP -> R.string.alarm_reason_clap
            },
        )
    }
    val statusDescription = stringResource(R.string.cd_status_shield)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val orb = (maxWidth * 0.5f).coerceIn(Dimens.HeroOrbMin, Dimens.HeroOrbMax)
            val rings = min(orb * 1.5f, maxWidth)
            Box(
                modifier = Modifier
                    .size(rings)
                    .semantics { contentDescription = "$statusDescription: $title" },
                contentAlignment = Alignment.Center,
            ) {
                PulseRings(
                    color = accent,
                    animate = hero !is HeroUiModel.Off,
                    periodMillis = if (hero is HeroUiModel.Ringing) 1100 else 2600,
                    modifier = Modifier.size(rings),
                )
                Box(
                    modifier = Modifier
                        .size(orb * 0.62f)
                        .background(
                            Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f))),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = hero,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                        contentKey = { it::class },
                        label = "heroGlyph",
                    ) { state ->
                        if (state is HeroUiModel.Arming) {
                            Text(
                                text = state.secondsLeft.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = ColorFFFF,
                            )
                        } else {
                            Icon(
                                painter = painterResource(state.iconRes()),
                                contentDescription = null,
                                tint = if (state is HeroUiModel.Off) MaterialTheme.colorScheme.surface else ColorFFFF,
                                modifier = Modifier.size(orb * 0.26f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dimens.SpaceL),
        )
        when (hero) {
            is HeroUiModel.Ringing -> PrimaryButton(
                text = stringResource(R.string.alarm_stop),
                onClick = onStopAlarm,
                leadingIcon = R.drawable.ic_close,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier.padding(top = Dimens.SpaceS),
            )
            HeroUiModel.Paused -> PrimaryButton(
                text = stringResource(R.string.hero_resume),
                onClick = onResume,
                modifier = Modifier.padding(top = Dimens.SpaceS),
            )
            else -> Unit
        }
    }
}

@DrawableRes
private fun HeroUiModel.iconRes(): Int = when (this) {
    HeroUiModel.Off -> R.drawable.ic_shield_off
    is HeroUiModel.Protected, is HeroUiModel.Arming -> R.drawable.ic_shield_check
    HeroUiModel.Paused -> R.drawable.ic_warning
    is HeroUiModel.Ringing -> R.drawable.ic_crisis_alert
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1F)
@Composable
private fun ProtectionStatusHeroProtectedPreview() {
    AntiTheftTheme(darkTheme = true) {
        ProtectionStatusHero(hero = HeroUiModel.Protected(2), onStopAlarm = {}, onResume = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F3FA)
@Composable
private fun ProtectionStatusHeroOffPreview() {
    AntiTheftTheme(darkTheme = false) {
        ProtectionStatusHero(hero = HeroUiModel.Off, onStopAlarm = {}, onResume = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1F)
@Composable
private fun ProtectionStatusHeroRingingPreview() {
    AntiTheftTheme(darkTheme = true) {
        ProtectionStatusHero(hero = HeroUiModel.Ringing(AlarmTrigger.MOTION), onStopAlarm = {}, onResume = {})
    }
}
