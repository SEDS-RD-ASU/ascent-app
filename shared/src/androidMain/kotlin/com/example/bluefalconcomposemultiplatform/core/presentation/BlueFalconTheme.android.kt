package com.example.bluefalconcomposemultiplatform.core.presentation

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowHorizon
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowSkyTop
import com.example.bluefalconcomposemultiplatform.ui.theme.LaunchGlowColorScheme
import com.example.bluefalconcomposemultiplatform.ui.theme.Typography

@Composable
actual fun BlueFalconTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    // Launch Glow is always dark, so the phone's light/dark setting and dynamic color are ignored
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = GlowSkyTop.toArgb()
            window.navigationBarColor = GlowHorizon.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = LaunchGlowColorScheme,
        typography = Typography,
        content = content
    )
}
