package com.anti.theft.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.Dimens

@Composable
fun HomeAdaptiveLayout(
    header: @Composable () -> Unit,
    hero: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= Dimens.ExpandedBreakpoint) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.TopCenter,
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = Dimens.ContentMaxWidth)
                        .fillMaxHeight()
                        .padding(horizontal = Dimens.SpaceXxl),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space3xl),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = Dimens.SpaceL),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl),
                    ) {
                        header()
                        hero()
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = Dimens.SpaceXl),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
                        content = content,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Dimens.SpaceXl)
                    .padding(bottom = Dimens.SpaceXxl),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            ) {
                header()
                hero()
                content()
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun HomeAdaptiveLayoutCompactPreview() {
    AntiTheftTheme(darkTheme = true) {
        HomeAdaptiveLayout(header = { Text("Header") }, hero = { Text("Hero") }) {
            Text("Guards", modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(widthDp = 840, heightDp = 480)
@Composable
private fun HomeAdaptiveLayoutExpandedPreview() {
    AntiTheftTheme(darkTheme = true) {
        HomeAdaptiveLayout(header = { Text("Header") }, hero = { Text("Hero") }) {
            Text("Guards", modifier = Modifier.fillMaxWidth())
        }
    }
}
