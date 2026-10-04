package com.anti.theft.presentation.home.components

import androidx.compose.ui.graphics.Color
import com.anti.theft.core.theme.Color22D3
import com.anti.theft.core.theme.Color4F7C
import com.anti.theft.core.theme.ColorFBBF
import com.anti.theft.domain.models.ProtectionFeature

/** Each guard's identity colour, used for its icon badge, switch and sensitivity selector. */
fun ProtectionFeature.accentColor(): Color = when (this) {
    ProtectionFeature.CHARGING -> ColorFBBF
    ProtectionFeature.MOTION -> Color4F7C
    ProtectionFeature.CLAP -> Color22D3
}
