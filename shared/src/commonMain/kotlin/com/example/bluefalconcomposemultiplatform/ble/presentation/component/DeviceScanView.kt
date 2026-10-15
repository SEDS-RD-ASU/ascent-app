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
import com.example.bluefalconcomposemultiplatform.ble.presentation.scanMessage
import kotlin.OptIn

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@Composable
fun DeviceScanView(
    state: BluetoothDeviceState,
    parsedPackets: Map<String, GooberPacket>,
    onEvent: (UiEvent) -> Unit,
    onOpenSettings: () -> Unit
) {
    val message = scanMessage(
        status = state.bluetoothStatus,
        audibleBoards = state.devices.size,
        scanningFor = state.scanStartedAt?.let { state.now - it }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        message?.let {
            item { ScanStatusMessage(it, onOpenSettings, Modifier.padding(bottom = 10.dp)) }
        }
        items(state.devices.values.toList().sortedByDescending { it.peripheral.name }) { device ->
            FoundDeviceCard(
                deviceName = if (!device.peripheral.name.isNullOrBlank()) device.peripheral.name else "No Name",
                macId = device.peripheral.uuid,
                rssi = device.peripheral.rssi,
                services = device.peripheral.services.values.toList(),
                connected = device.connected,
                parsedPacket = parsedPackets[device.peripheral.uuid],
                onEvent = onEvent
            )
        }
    }
}
