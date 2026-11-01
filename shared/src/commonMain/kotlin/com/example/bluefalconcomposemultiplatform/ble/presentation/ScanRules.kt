package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.ble.data.BluetoothStatus
import dev.bluefalcon.BluetoothNotEnabledException
import dev.bluefalcon.BluetoothPermissionException
import dev.bluefalcon.BluetoothUnsupportedException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// What the status line above the device list says
enum class ScanMessage { BluetoothOff, NoPermission, Unsupported, StartingBluetooth, Searching, NotFound }

// A board we're not connected to counts as gone after this long without a broadcast
val GONE_AFTER: Duration = 10.seconds

// Searching turns into "No ASCENT found yet" after this long without hearing a board
val NOT_FOUND_AFTER: Duration = 15.seconds

// Signal strength bars (0-4) for an RSSI reading in dBm
fun signalBars(rssi: Float?): Int = when {
    rssi == null -> 0
    rssi >= -60f -> 4
    rssi >= -70f -> 3
    rssi >= -80f -> 2
    rssi >= -90f -> 1
    else -> 0
}

// Smooth RSSI readings so the bars don't flicker; the first reading is used as is
fun smoothRssi(previous: Float?, reading: Float): Float =
    if (previous == null) reading else previous * 0.7f + reading * 0.3f

// How long ago a board was last heard, or null if it never has been
fun sinceLastSeen(now: Duration, lastSeenAt: Duration?): Duration? =
    lastSeenAt?.let { (now - it).coerceAtLeast(Duration.ZERO) }

// Connected boards stop broadcasting, so only boards we're not connected to can go quiet
fun isGone(sinceLastSeen: Duration?, connected: Boolean): Boolean =
    !connected && sinceLastSeen != null && sinceLastSeen >= GONE_AFTER

// Bluetooth problems first; then nothing while a board can be heard; then searching / not found
fun scanMessage(status: BluetoothStatus, audibleBoards: Int, scanningFor: Duration?): ScanMessage? =
    when (status) {
        BluetoothStatus.Off -> ScanMessage.BluetoothOff
        BluetoothStatus.NoPermission -> ScanMessage.NoPermission
        BluetoothStatus.Unsupported -> ScanMessage.Unsupported
        BluetoothStatus.Starting -> ScanMessage.StartingBluetooth
        BluetoothStatus.Ready -> when {
            audibleBoards > 0 -> null
            scanningFor != null && scanningFor >= NOT_FOUND_AFTER -> ScanMessage.NotFound
            else -> ScanMessage.Searching
        }
    }

// BlueFalcon's scan() throws one of these on iOS when Bluetooth isn't ready
fun bluetoothStatusFor(error: Throwable): BluetoothStatus = when (error) {
    is BluetoothNotEnabledException -> BluetoothStatus.Off
    is BluetoothPermissionException -> BluetoothStatus.NoPermission
    is BluetoothUnsupportedException -> BluetoothStatus.Unsupported
    else -> BluetoothStatus.Starting
}
