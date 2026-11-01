package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.runtime.Composable

// Calls onBack when the platform's own Back control is used (Android's Back button or gesture)
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
