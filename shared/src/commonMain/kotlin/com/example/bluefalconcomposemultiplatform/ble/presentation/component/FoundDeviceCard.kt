package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.GooberMsgType
import com.example.bluefalconcomposemultiplatform.GooberPacket
import com.example.bluefalconcomposemultiplatform.GooberPayload
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import com.example.bluefalconcomposemultiplatform.ble.presentation.signalBars
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandPurple
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandRed
import com.example.bluefalconcomposemultiplatform.ui.theme.ConnectedGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.OnAsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary
import dev.bluefalcon.BluetoothService
import kotlin.OptIn
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi

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
    goneFor: Duration?,
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
            val normalizedCharUuid = normalizeUuidForComparison(characteristic.uuid.toString())
            targetUuidStrings.any { target ->
                normalizedCharUuid == normalizeUuidForComparison(target)
            }
        }

    // A board that has gone quiet is dimmed and can't be connected to until it's heard again
    val gone = goneFor != null
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .alpha(if (gone) 0.42f else 1f)
            .clip(shape)
            .background(CardBackground)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$deviceName",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    when {
                        connected -> {
                            Box(Modifier.size(8.dp).background(ConnectedGreen, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("Connected", color = TextSecondary, fontSize = 13.sp)
                        }
                        goneFor != null -> Text(
                            "Not seen for ${goneFor.inWholeSeconds} s",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        else -> {
                            SignalBars(signalBars(rssi))
                            rssi?.let {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${it.roundToInt()} dBm",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
            if (connected) {
                OutlinedButton(
                    onClick = { onEvent(UiEvent.OnDisconnectClick(macId)) },
                    border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.28f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { onEvent(UiEvent.OnConnectClick(macId)) },
                    enabled = !gone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AsuGold,
                        contentColor = OnAsuGold,
                        disabledContainerColor = SignalTrack,
                        disabledContentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Telemetry and commands for a connected board
        if (connected) {
            PacketPanel(parsedPacket, Modifier.padding(top = 12.dp))
            // Commands need the target characteristic, which appears once services are discovered
            if (targetCharacteristic != null) {
                CommandPanel(macId, onEvent, Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun PacketPanel(packet: GooberPacket?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(12.dp)
    ) {
        Text(
            "LATEST PACKET",
            color = AsuGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        if (packet == null) {
            Text(
                "No data received",
                color = TextSecondary,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 6.dp)
            )
            return@Column
        }

        // Packet header on one line, e.g. "DEV 0x01 · MODE 0x02 · SEQ 0x2A · POST_TELEM · 33 B"
        Text(
            "DEV ${hex(packet.devId)} · MODE ${hex(packet.devMode)} · SEQ ${hex(packet.seqId)} · " +
                "${packet.msgCls.name.removePrefix("MSG_TYPE_")} · ${packet.payloadSize} B",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        )

        val telemetry = (packet.payload as? GooberPayload.Telemetry)?.payload ?: return@Column
        TelemetryRow("Altitude AGL", "${formatFloat(telemetry.altitudeAgl)} m")
        TelemetryRow("Vertical velocity", "${formatFloat(telemetry.verticalVelocity)} m/s")
        TelemetryRow("X acceleration", "${formatFloat(telemetry.xAcc)} m/s²")
        TelemetryRow("Gyro X", formatFloat(telemetry.gyrX))
        TelemetryRow("Pyro state", formatPyroState(telemetry.pyroState))
        TelemetryRow("Satellites", "${telemetry.sats}")
        TelemetryRow("Flight state", hex(telemetry.flightState))
        TelemetryRow("Battery", "${formatDouble(telemetry.batteryVoltage.toDouble() / 2500.0)} V")
        TelemetryRow("Timestamp", "${telemetry.timestamp}")
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun hex(value: UByte): String = "0x${value.toString(16).padStart(2, '0').uppercase()}"

// What each command button's color means; shown in the key under the buttons
private enum class CommandKind(val color: Color, val title: String, val description: String) {
    SYSTEM(CommandRed, "System", "Locks the radio or restarts the board"),
    AUXILIARY(CommandPurple, "Auxiliary", "Switches on the AUX output"),
    WAKE(CommandGreen, "Wake", "Wakes the board up")
}

// Commands the app can send. Apogee and Mains (MSG_TYPE_REQ_POP_*) are deliberately left out:
// Bluetooth can't reach the rocket in flight and the protocol doesn't confirm they were received.
private enum class BoardCommand(
    val label: String,
    val kind: CommandKind,
    val msgType: GooberMsgType,
    val confirmTitle: String,
    val confirmMessage: String
) {
    TX_LOCK("TX Lock", CommandKind.SYSTEM, GooberMsgType.MSG_TYPE_REQ_TXLOCK_ACTIVATE,
        "Confirm TX Lock", "Are you sure you want to activate TX Lock?"),
    AUX("AUX", CommandKind.AUXILIARY, GooberMsgType.MSG_TYPE_REQ_AUX_ACTIVATE,
        "Confirm AUX", "Are you sure you want to activate AUX?"),
    REBOOT("Reboot", CommandKind.SYSTEM, GooberMsgType.MSG_TYPE_REQ_REBOOT,
        "Confirm Reboot", "Are you sure you want to reboot the device?"),
    WAKEUP("Wakeup", CommandKind.WAKE, GooberMsgType.MSG_TYPE_REQ_WAKEUP,
        "Confirm Wakeup", "Are you sure you want to wake up the device?")
}

@Composable
private fun CommandPanel(macId: String, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    // The command waiting for confirmation, if any
    var pendingCommand by remember { mutableStateOf<BoardCommand?>(null) }

    Column(modifier.fillMaxWidth()) {
        listOf(
            listOf(BoardCommand.TX_LOCK, BoardCommand.AUX),
            listOf(BoardCommand.REBOOT, BoardCommand.WAKEUP)
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { command ->
                    Button(
                        onClick = { pendingCommand = command },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = command.kind.color,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(command.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        CommandLegend(Modifier.padding(top = 6.dp))
    }

    pendingCommand?.let { command ->
        ConfirmationDialog(
            command = command,
            onConfirm = {
                onEvent(UiEvent.OnSendSingleByteCommand(macId, command.msgType.value))
                pendingCommand = null
            },
            onDismiss = { pendingCommand = null }
        )
    }
}

@Composable
private fun CommandLegend(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = CardBorder)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CommandKind.entries.forEach { kind ->
                Row(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(10.dp)
                            .background(kind.color, RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(kind.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(kind.description, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    command: BoardCommand,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(command.confirmTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = { Text(command.confirmMessage, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
