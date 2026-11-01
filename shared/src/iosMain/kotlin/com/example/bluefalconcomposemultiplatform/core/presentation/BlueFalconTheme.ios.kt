package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.bluefalconcomposemultiplatform.ui.theme.LaunchGlowColorScheme
import com.example.bluefalconcomposemultiplatform.ui.theme.Typography

@Composable
actual fun BlueFalconTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    // Launch Glow is always dark, so the phone's light/dark setting doesn't change the colors
    MaterialTheme(
        colorScheme = LaunchGlowColorScheme,
        typography = Typography,
        content = content
    )
}
