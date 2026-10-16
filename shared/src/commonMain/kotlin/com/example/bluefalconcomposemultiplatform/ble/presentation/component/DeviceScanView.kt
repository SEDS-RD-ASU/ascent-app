package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.GooberPacket
import com.example.bluefalconcomposemultiplatform.ble.presentation.BluetoothDeviceState
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import com.example.bluefalconcomposemultiplatform.ble.presentation.isGone
import com.example.bluefalconcomposemultiplatform.ble.presentation.scanMessage
import com.example.bluefalconcomposemultiplatform.ble.presentation.sinceLastSeen
import kotlin.OptIn

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@Composable
fun DeviceScanView(
    state: BluetoothDeviceState,
    parsedPackets: Map<String, GooberPacket>,
    onEvent: (UiEvent) -> Unit,
    onOpenSettings: () -> Unit
) {
    // Same order as before (by name), so cards don't jump around as signals change
    val devices = state.devices.values.toList().sortedByDescending { it.peripheral.name }
    // How long each board has been quiet, for boards that count as gone
    val goneFor = devices.associate { device ->
        device.peripheral.uuid to sinceLastSeen(state.now, device.lastSeenAt)
            ?.takeIf { isGone(it, device.connected) }
    }
    val message = scanMessage(
        status = state.bluetoothStatus,
        audibleBoards = devices.count { goneFor[it.peripheral.uuid] == null },
        scanningFor = state.scanStartedAt?.let { state.now - it }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        message?.let {
            item { ScanStatusMessage(it, onOpenSettings, Modifier.padding(bottom = 10.dp)) }
        }
        items(devices) { device ->
            FoundDeviceCard(
                deviceName = if (!device.peripheral.name.isNullOrBlank()) device.peripheral.name else "No Name",
                macId = device.peripheral.uuid,
                rssi = device.rssi,
                goneFor = goneFor[device.peripheral.uuid],
                services = device.peripheral.services.values.toList(),
                connected = device.connected,
                parsedPacket = parsedPackets[device.peripheral.uuid],
                onEvent = onEvent
            )
        }
    }
}
