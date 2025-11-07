package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.GooberPacket
import com.example.bluefalconcomposemultiplatform.ble.presentation.BluetoothDeviceState
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import kotlin.OptIn

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@Composable
fun DeviceScanView(
    state: BluetoothDeviceState,
    parsedPackets: Map<String, GooberPacket>,
    onEvent: (UiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(10.dp),
    ) {
        LazyColumn {
            items(state.devices.values.toList().sortedByDescending { it.peripheral.name }) { device ->
                // Debug logging
                // println("DEBUG UI: Rendering device ${device.peripheral.name}")
                // println("  - Connected: ${device.connected}")
                // println("  - Services in peripheral: ${device.peripheral.services.size}")
                val servicesList = device.peripheral.services.values.toList()
                // println("  - Services list: ${servicesList.size}")
                
                FoundDeviceCard(
                    deviceName = if (!device.peripheral.name.isNullOrBlank()) device.peripheral.name else "No Name",
                    macId = device.peripheral.uuid,
                    rssi = device.peripheral.rssi,
                    services = servicesList,
                    connected = device.connected,
                    parsedPacket = parsedPackets[device.peripheral.uuid],
                    onEvent = onEvent
                )
            }
        }
    }
}