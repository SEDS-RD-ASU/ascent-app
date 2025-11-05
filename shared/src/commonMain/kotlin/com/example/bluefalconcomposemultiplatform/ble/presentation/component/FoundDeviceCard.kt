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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.GooberMsgType
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

// Helper function to format pyro state as "T T F F" format
// Each bit represents a pyro channel: bit 0 = PYRO_CHANNEL_1, bit 1 = PYRO_CHANNEL_2, etc.
// T = True (bit is set, pyro has continuity), F = False (bit is clear, no continuity)
private fun formatPyroState(pyroState: kotlin.UByte): String {
    val channel1 = if ((pyroState.toInt() and (1 shl 0)) != 0) "T" else "F"
    val channel2 = if ((pyroState.toInt() and (1 shl 1)) != 0) "T" else "F"
    val channel3 = if ((pyroState.toInt() and (1 shl 2)) != 0) "T" else "F"
    val channel4 = if ((pyroState.toInt() and (1 shl 3)) != 0) "T" else "F"
    return "$channel1 $channel2 $channel3 $channel4"
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
            // println("DEBUG UI: Checking characteristic UUID: $characteristicUuidString")
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
                                        formatPyroState(telemetry.pyroState),
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        " (0x${telemetry.pyroState.toString(16).padStart(2, '0').uppercase()})",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(start = 4.dp)
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
                                        "${formatDouble(telemetry.batteryVoltage.toDouble() / 2500.0)} V",
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
    // State to track which confirmation dialog to show
    var confirmationDialogType by remember { mutableStateOf<ConfirmationDialogType?>(null) }
    
    // Placeholder functions for buttons
    fun onTxLockClick() {
        confirmationDialogType = ConfirmationDialogType.TX_LOCK
    }
    
    fun onAuxClick() {
        confirmationDialogType = ConfirmationDialogType.AUX
    }
    
    fun onRebootClick() {
        confirmationDialogType = ConfirmationDialogType.REBOOT
    }
    
    fun onWakeupClick() {
        confirmationDialogType = ConfirmationDialogType.WAKEUP
    }
    
    fun onApogeeClick() {
        confirmationDialogType = ConfirmationDialogType.APOGEE
    }
    
    fun onMainsClick() {
        confirmationDialogType = ConfirmationDialogType.MAINS
    }
    
    // Execute the actual action after confirmation
    fun executeAction(type: ConfirmationDialogType) {
        when (type) {
            ConfirmationDialogType.TX_LOCK -> {
                onEvent(UiEvent.OnSendSingleByteCommand(macId, GooberMsgType.MSG_TYPE_REQ_TXLOCK_ACTIVATE.value))
            }
            ConfirmationDialogType.AUX -> {
                onEvent(UiEvent.OnSendSingleByteCommand(macId, GooberMsgType.MSG_TYPE_REQ_AUX_ACTIVATE.value))
            }
            ConfirmationDialogType.REBOOT -> {
                onEvent(UiEvent.OnSendSingleByteCommand(macId, GooberMsgType.MSG_TYPE_REQ_REBOOT.value))
            }
            ConfirmationDialogType.WAKEUP -> {
                onEvent(UiEvent.OnSendSingleByteCommand(macId, GooberMsgType.MSG_TYPE_REQ_WAKEUP.value))
            }
            ConfirmationDialogType.APOGEE -> {
                onEvent(UiEvent.OnSendSingleByteCommand(macId, GooberMsgType.MSG_TYPE_REQ_POP_APOGEE.value))
            }
            ConfirmationDialogType.MAINS -> {
                onEvent(UiEvent.OnSendSingleByteCommand(macId, GooberMsgType.MSG_TYPE_REQ_POP_MAINS.value))
            }
        }
    }
    
    Column(
        modifier = Modifier
            .padding(4.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(6.dp)
    ) {
        
        // Top row: TX Lock, AUX: NA, Reboot
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            // TX Lock button (red)
            Button(
                onClick = { onTxLockClick() },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F) // Red
                )
            ) {
                Text("TX Lock", color = Color.White, fontSize = 10.sp)
            }
            
            // AUX: NA button (green)
            Button(
                onClick = { onAuxClick() },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8E24AA) // Purple
                )
            ) {
                Text("AUX", color = Color.White, fontSize = 10.sp)
            }
            
            // Reboot button (red)
            Button(
                onClick = { onRebootClick() },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F) // Red
                )
            ) {
                Text("Reboot", color = Color.White, fontSize = 10.sp)
            }
        }
        
        // Bottom row: Wakeup, Apogee, Mains
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Wakeup button (green)
            Button(
                onClick = { onWakeupClick() },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF388E3C) // Green
                )
            ) {
                Text("Wakeup", color = Color.White, fontSize = 10.sp)
            }
            
            // Apogee button (blue)
            Button(
                onClick = { onApogeeClick() },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2) // Blue
                )
            ) {
                Text("Apogee", color = Color.White, fontSize = 10.sp)
            }
            
            // Mains button (dark gray with blue border)
            Button(
                onClick = { onMainsClick() },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2) // Blue
                )
            ) {
                Text("Mains", color = Color.White, fontSize = 10.sp)
            }
        }
        
        // Confirmation Dialog
        confirmationDialogType?.let { dialogType ->
            ConfirmationDialog(
                dialogType = dialogType,
                onConfirm = {
                    executeAction(dialogType)
                    confirmationDialogType = null
                },
                onDismiss = {
                    confirmationDialogType = null
                }
            )
        }
    }
}

// Enum to track which confirmation dialog to show
private enum class ConfirmationDialogType(
    val title: String,
    val message: String
) {
    TX_LOCK("Confirm TX Lock", "Are you sure you want to activate TX Lock?"),
    AUX("Confirm AUX", "Are you sure you want to activate AUX?"),
    REBOOT("Confirm Reboot", "Are you sure you want to reboot the device?"),
    WAKEUP("Confirm Wakeup", "Are you sure you want to wake up the device?"),
    APOGEE("Confirm Apogee", "Are you sure you want to trigger Apogee?"),
    MAINS("Confirm Mains", "Are you sure you want to trigger Mains?")
}

@Composable
private fun ConfirmationDialog(
    dialogType: ConfirmationDialogType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = dialogType.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = dialogType.message,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
