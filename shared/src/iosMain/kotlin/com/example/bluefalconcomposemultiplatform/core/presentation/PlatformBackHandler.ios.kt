package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system Back button here; the screen's back arrow is the way back
}
