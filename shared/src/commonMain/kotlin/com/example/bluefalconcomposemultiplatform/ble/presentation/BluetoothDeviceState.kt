package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.ble.data.BluetoothStatus
import kotlin.time.Duration

data class BluetoothDeviceState(
    val devices: HashMap<String, EnhancedBluetoothPeripheral> = HashMap(),
    val updateVersion: Int = 0,
    val bluetoothStatus: BluetoothStatus = BluetoothStatus.Starting,
    // When the current scan started and when the state was last refreshed, on the view model's clock
    val scanStartedAt: Duration? = null,
    val now: Duration = Duration.ZERO
)
