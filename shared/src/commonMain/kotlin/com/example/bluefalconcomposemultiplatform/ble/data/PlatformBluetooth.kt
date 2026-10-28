package com.example.bluefalconcomposemultiplatform.ble.data

// Checks that BlueFalcon can't do for us: BlueFalcon 2.2.4 always reports Ready on Android,
// and it can't open the app's settings page on either platform
interface PlatformBluetooth {
    // A problem that stops scanning, or null if nothing is wrong as far as the platform can tell
    fun problem(): BluetoothStatus?

    fun openAppSettings()
}
