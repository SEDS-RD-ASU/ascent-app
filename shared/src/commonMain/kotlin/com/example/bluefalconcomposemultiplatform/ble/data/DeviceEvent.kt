package com.example.bluefalconcomposemultiplatform.ble.data

import dev.bluefalcon.BluetoothCharacteristic
import dev.bluefalcon.BluetoothPeripheral

sealed interface DeviceEvent {
    data class OnDeviceConnected(val macId: String, val peripheral: BluetoothPeripheral): DeviceEvent
    data class OnDeviceDisconnected(val macId: String): DeviceEvent
    data class OnServicesDiscovered(val peripheral: BluetoothPeripheral): DeviceEvent
    data class OnCharacteristicReadComplete(val macId: String, val characteristic: BluetoothCharacteristic): DeviceEvent
    data class OnCharacteristicWriteComplete(val macId: String, val characteristic: BluetoothCharacteristic, val success: Boolean): DeviceEvent
}