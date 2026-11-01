package com.example.bluefalconcomposemultiplatform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.ble.presentation.BluetoothDeviceViewModel
import com.example.bluefalconcomposemultiplatform.ble.presentation.EnhancedBluetoothPeripheral
import com.example.bluefalconcomposemultiplatform.ble.presentation.component.DeviceScanView
import com.example.bluefalconcomposemultiplatform.checklist.BoardSnapshot
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistEntryRow
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistScreen
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistViewModel
import com.example.bluefalconcomposemultiplatform.checklist.PREFLIGHT_STEPS
import com.example.bluefalconcomposemultiplatform.checklist.boardFocus
import com.example.bluefalconcomposemultiplatform.checklist.currentEpochMillis
import com.example.bluefalconcomposemultiplatform.checklist.entryLine
import com.example.bluefalconcomposemultiplatform.checklist.formatClockTime
import com.example.bluefalconcomposemultiplatform.checklist.progress
import com.example.bluefalconcomposemultiplatform.checklist.staleHours
import com.example.bluefalconcomposemultiplatform.core.presentation.AppHeader
import com.example.bluefalconcomposemultiplatform.core.presentation.BlueFalconTheme
import com.example.bluefalconcomposemultiplatform.core.presentation.LaunchGlowBackground
import com.example.bluefalconcomposemultiplatform.core.presentation.PlatformBackHandler
import com.example.bluefalconcomposemultiplatform.di.AppModule
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import dev.icerock.moko.mvvm.compose.getViewModel
import dev.icerock.moko.mvvm.compose.viewModelFactory

// The two screens: the scan screen (the home screen for now) and the pre-flight checklist
private enum class Screen { Scan, Checklist }

@Composable
fun App(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    appModule: AppModule
) {
    BlueFalconTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor
    ) {
        val viewModel = getViewModel(
            key = "bluetooth-device-screen",
            factory = viewModelFactory {
                BluetoothDeviceViewModel(appModule.blueFalcon, appModule.platformBluetooth)
            }
        )
        val checklistViewModel = getViewModel(
            key = "preflight-checklist",
            factory = viewModelFactory {
                ChecklistViewModel(appModule.checklistStorage)
            }
        )

        val state by viewModel.deviceState.collectAsState()
        val parsedPackets by viewModel.parsedPackets.collectAsState()
        val ticks by checklistViewModel.ticks.collectAsState()
        val initials by checklistViewModel.initials.collectAsState()
        var screen by rememberSaveable { mutableStateOf(Screen.Scan) }

        // Readings are worked out from the live Bluetooth state on every update; they're never saved
        val focus = boardFocus(state.devices.values.map { it.toBoardSnapshot() }, state.now)

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = TextPrimary
        ) {
            Box(Modifier.fillMaxSize()) {
                // The background fills the whole screen; content stays clear of the notch and home indicator
                LaunchGlowBackground()
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    when (screen) {
                        Screen.Scan -> {
                            AppHeader()
                            ChecklistEntryRow(
                                summary = entryLine(
                                    progress(PREFLIGHT_STEPS, ticks),
                                    ticks.startedAtMillis?.let(::formatClockTime)
                                ),
                                onClick = { screen = Screen.Checklist },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            )
                            DeviceScanView(
                                state = state,
                                parsedPackets = parsedPackets,
                                onEvent = viewModel::onEvent,
                                onOpenSettings = appModule.platformBluetooth::openAppSettings
                            )
                        }
                        Screen.Checklist -> {
                            PlatformBackHandler { screen = Screen.Scan }
                            ChecklistScreen(
                                ticks = ticks,
                                initials = initials,
                                focus = focus,
                                staleHours = staleHours(ticks.startedAtMillis, currentEpochMillis()),
                                onTick = checklistViewModel::tick,
                                onUntick = checklistViewModel::untick,
                                onSetInitials = checklistViewModel::setInitials,
                                onStartNew = checklistViewModel::startNew,
                                onBack = { screen = Screen.Scan }
                            )
                        }
                    }
                }
            }
        }
    }
}

// What the checklist needs to know about a board, including its latest telemetry
private fun EnhancedBluetoothPeripheral.toBoardSnapshot() = BoardSnapshot(
    name = peripheral.name?.takeIf { it.isNotBlank() } ?: "No Name",
    connected = connected,
    telemetry = lastTelemetry,
    telemetryAt = lastTelemetryAt
)
