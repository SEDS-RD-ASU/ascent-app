package com.example.bluefalconcomposemultiplatform.ble.presentation

import dev.bluefalcon.BluetoothPeripheral
import kotlin.time.Duration

data class EnhancedBluetoothPeripheral(
    val connected: Boolean,
    val peripheral: BluetoothPeripheral,
    // Smoothed signal strength from the board's broadcasts, in dBm
    val rssi: Float? = null,
    // When the board was last heard, on the view model's clock
    val lastSeenAt: Duration? = null
)
