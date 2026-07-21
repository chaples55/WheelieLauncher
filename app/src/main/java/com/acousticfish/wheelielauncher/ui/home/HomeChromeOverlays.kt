package com.acousticfish.wheelielauncher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acousticfish.wheelielauncher.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BoxScope.HomeChromeOverlays(
    showClock: Boolean,
    showEqButton: Boolean,
    showSkipButtons: Boolean,
    onEqClick: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onClockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (showEqButton) {
            ChromeIconButton(
                drawable = R.drawable.equalizer_24,
                contentDescription = stringResource(R.string.eq_shortcut),
                onClick = onEqClick,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        if (showClock) {
            FloatingClock(
                onClick = onClockClick,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        if (showSkipButtons) {
            ChromeIconButton(
                drawable = R.drawable.skip_previous_24,
                contentDescription = stringResource(R.string.skip_previous),
                onClick = onSkipPrevious,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            ChromeIconButton(
                drawable = R.drawable.skip_next_24,
                contentDescription = stringResource(R.string.skip_next),
                onClick = onSkipNext,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun FloatingClock(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(formatClock(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            text = formatClock(now)
            val delayMs = 60_000L - (now % 60_000L)
            delay(delayMs.coerceAtLeast(1_000L))
        }
    }
    Text(
        text = text,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick,
            )
            .padding(top = 4.dp, end = 2.dp, start = 8.dp, bottom = 8.dp),
        style = TextStyle(
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.55f),
                blurRadius = 8f,
            ),
        ),
    )
}

@Composable
private fun ChromeIconButton(
    drawable: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(drawable),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

private fun formatClock(epochMs: Long): String {
    val fmt = SimpleDateFormat("h:mm", Locale.getDefault())
    return fmt.format(Date(epochMs))
}
