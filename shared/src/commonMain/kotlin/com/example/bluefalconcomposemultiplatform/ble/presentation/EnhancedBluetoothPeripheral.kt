package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.GooberPostTelemetryPayload
import dev.bluefalcon.BluetoothPeripheral
import kotlin.time.Duration

data class EnhancedBluetoothPeripheral(
    val connected: Boolean,
    val peripheral: BluetoothPeripheral,
    // Smoothed signal strength from the board's broadcasts, in dBm
    val rssi: Float? = null,
    // When the board was last heard, on the view model's clock
    val lastSeenAt: Duration? = null,
    // The board's latest telemetry packet and when it arrived, on the view model's clock
    val lastTelemetry: GooberPostTelemetryPayload? = null,
    val lastTelemetryAt: Duration? = null,
    // The board's reads come back as single bytes: first-byte firmware (flight, dev), which sends its
    // battery but no telemetry over Bluetooth
    val batteryOnlyReads: Boolean = false,
    // When the board's pops were armed, on the wall clock (milliseconds since 1970); null when not armed
    val pyroArmedAtMillis: Long? = null
)
