package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.GooberPacket
import com.example.bluefalconcomposemultiplatform.GooberPayload
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import dev.bluefalcon.BluetoothCharacteristic
import dev.bluefalcon.BluetoothService
import kotlin.OptIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Multiplatform-compatible number formatting helper
private fun formatFloat(value: Float, decimals: Int = 2): String {
    // Calculate 10^decimals without using pow
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    val factorFloat = factor.toFloat()
    val rounded = (value * factorFloat).toInt().toFloat() / factorFloat
    val str = rounded.toString()
    val parts = str.split('.')
    val integerPart = parts[0]
    val decimalPart = parts.getOrElse(1) { "" }.padEnd(decimals, '0').take(decimals)
    return "$integerPart.$decimalPart"
}

// Multiplatform-compatible double formatting helper
private fun formatDouble(value: Double, decimals: Int = 2): String {
    // Calculate 10^decimals without using pow
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    val rounded = (value * factor).toInt().toDouble() / factor
    val str = rounded.toString()
    val parts = str.split('.')
    val integerPart = parts[0]
    val decimalPart = parts.getOrElse(1) { "" }.padEnd(decimals, '0').take(decimals)
    return "$integerPart.$decimalPart"
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun FoundDeviceCard(
    deviceName: String?,
    macId: String,
    rssi: Float?,
    services: List<BluetoothService>,
    connected: Boolean,
    parsedPacket: GooberPacket?,
    onEvent: (UiEvent) -> Unit
) {
    // Helper function to normalize UUID for comparison (handles short and full formats)
    fun normalizeUuidForComparison(uuid: String): String {
        val cleaned = uuid.replace("-", "").lowercase()
        return if (cleaned.length <= 8) {
            cleaned.takeLast(4).padStart(4, '0')
        } else {
            cleaned.take(8).takeLast(4).padStart(4, '0')
        }
    }
    
    // Target UUID: 00000001-0000-1000-8000-00805f9b34fb
    // Also handle short UUID formats (0001, 00000001)
    val targetUuidStrings = listOf(
        "00000001-0000-1000-8000-00805f9b34fb",  // Full UUID
        "0001",                                   // Short 16-bit
        "00000001"                                // Alternative short format
    )
    
    // Find the characteristic with UUID 00000001 from all services
    val targetCharacteristic = services.flatMap { it.characteristics }
        .firstOrNull { characteristic ->
            val characteristicUuidString = characteristic.uuid.toString()
            println("DEBUG UI: Checking characteristic UUID: $characteristicUuidString")
            // Compare UUID strings (normalize for comparison)
            val normalizedCharUuid = normalizeUuidForComparison(characteristicUuidString)
            targetUuidStrings.any { target ->
                val normalizedTarget = normalizeUuidForComparison(target)
                normalizedCharUuid == normalizedTarget
            }
        }
    Column (
        modifier = Modifier
            .padding(10.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(10.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(16.dp),

        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "$deviceName",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                rssi?.let {
                    Text(
                        text = "RSSI: $rssi",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = "${if (connected) "✅ Connected" else "❌ Disconnected"}",
                    color = if (connected) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (!connected) {
                Button(
                    onClick = {
                        onEvent(UiEvent.OnConnectClick(macId))
                    }
                ) {
                    Text("Connect")
                }
            } else {
                Button(
                    onClick = {
                        onEvent(UiEvent.OnDisconnectClick(macId))
                    }
                ) {
                    Text("Disconnect")
                }
            }
        }
        
        // Display parsed Goober packet if connected
        if (connected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    "Latest GOOBER Packet:",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                parsedPacket?.let { packet ->
                    Column {
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                "DEV_ID: ",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "0x${packet.devId.toString(16).padStart(2, '0').uppercase()}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                "DEV_MODE: ",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "0x${packet.devMode.toString(16).padStart(2, '0').uppercase()}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                "SEQ_ID: ",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "0x${packet.seqId.toString(16).padStart(2, '0').uppercase()}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                "MSG_CLS: ",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                packet.msgCls.name,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                "PAYLOAD_SIZE: ",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${packet.payloadSize} bytes",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        // Display telemetry payload if available
                        if (packet.payload is GooberPayload.Telemetry) {
                            val telemetry = (packet.payload as GooberPayload.Telemetry).payload
                            Column(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    "Telemetry Payload:",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Timestamp: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${telemetry.timestamp}",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Latitude: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${telemetry.latitude / 1_000_000.0}°",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Longitude: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${telemetry.longitude / 1_000_000.0}°",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Altitude AGL: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${formatFloat(telemetry.altitudeAgl)} m",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Vertical Velocity: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${formatFloat(telemetry.verticalVelocity)} m/s",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "X Acceleration: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${formatFloat(telemetry.xAcc)} m/s²",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Gyro X: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        formatFloat(telemetry.gyrX),
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Pyro State: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "0x${telemetry.pyroState.toString(16).padStart(2, '0').uppercase()}",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Satellites: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${telemetry.sats}",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Flight State: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "0x${telemetry.flightState.toString(16).padStart(2, '0').uppercase()}",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                                    Text(
                                        "Battery Voltage: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${formatDouble(telemetry.batteryVoltage.toInt() / 1000.0)} V",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                } ?: Text(
                    "No data received",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            
            // Show characteristic with UUID 00000001 if found
            targetCharacteristic?.let { characteristic ->
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    CharacteristicsRow(macId, characteristic, onEvent)
                }
            }
        }
    }
}

// ServiceRow removed - no longer displaying service name or UUID

@Composable
fun CharacteristicsRow(
    macId: String,
    characteristic: BluetoothCharacteristic,
    onEvent: (UiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Button(
                onClick = {
                    onEvent(UiEvent.OnReadCharacteristic(macId, characteristic))
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text("Read", fontSize = 8.sp)
            }
            Button(
                onClick = {
                    onEvent(UiEvent.OnWriteCharacteristic(macId, characteristic, "123"))
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text("Write", fontSize = 8.sp)
            }
        }
    }
}
