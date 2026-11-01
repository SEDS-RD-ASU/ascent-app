package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ble.presentation.BoardCommand
import com.example.bluefalconcomposemultiplatform.ble.presentation.Confirmation
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import com.example.bluefalconcomposemultiplatform.ui.theme.PlainButton
import com.example.bluefalconcomposemultiplatform.ui.theme.ProblemRedText
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary

// The connected card's commands, and the dialog that confirms the ones that ask first
@Composable
fun CommandPanel(
    boardName: String,
    macId: String,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // The command whose dialog is open, if any
    var pending by remember { mutableStateOf<BoardCommand?>(null) }

    // AUX goes straight away; everything else opens its dialog
    fun tap(command: BoardCommand) {
        if (command.confirmation == Confirmation.None) onEvent(UiEvent.OnSendCommand(macId, command))
        else pending = command
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "COMMANDS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        listOf(
            listOf(BoardCommand.TX_LOCK, BoardCommand.REBOOT),
            listOf(BoardCommand.AUX_ON, BoardCommand.AUX_OFF)
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { command ->
                    Button(
                        onClick = { tap(command) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PlainButton, contentColor = TextPrimary),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(command.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Red is kept for what can't be undone
        OutlinedButton(
            onClick = { tap(BoardCommand.ERASE_FLASH) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.5.dp, ProblemRedText),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProblemRedText),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(BoardCommand.ERASE_FLASH.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }

    pending?.let { command ->
        CommandDialog(
            command = command,
            boardName = boardName,
            onSend = {
                onEvent(UiEvent.OnSendCommand(macId, command))
                pending = null
            },
            onDismiss = { pending = null }
        )
    }
}

@Composable
private fun CommandDialog(command: BoardCommand, boardName: String, onSend: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle(command, boardName), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = { Text(dialogText(command), fontSize = 14.sp) },
        confirmButton = {
            if (command.confirmation == Confirmation.Tap) {
                TextButton(onClick = onSend) { Text(command.label) }
            } else {
                HoldToConfirmButton(
                    label = if (command == BoardCommand.ERASE_FLASH) "Hold to erase" else "Hold to fire",
                    onConfirmed = onSend
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Dialog wording, as in the board commands spec. AUX On and AUX Off have no dialog.
private fun dialogTitle(command: BoardCommand, board: String): String = when (command) {
    BoardCommand.POP_APOGEE -> "Fire APOGEE on $board?"
    BoardCommand.POP_MAINS -> "Fire MAINS on $board?"
    BoardCommand.ERASE_FLASH -> "Erase all flash on $board?"
    BoardCommand.TX_LOCK -> "TX Lock $board?"
    BoardCommand.REBOOT -> "Reboot $board?"
    BoardCommand.AUX_ON, BoardCommand.AUX_OFF -> "${command.label} $board?"
}

private fun dialogText(command: BoardCommand): String = when (command) {
    BoardCommand.POP_APOGEE, BoardCommand.POP_MAINS ->
        "Fires pyro channel ${command.pyroChannel} for 250 ms. The board doesn't check continuity first."
    BoardCommand.ERASE_FLASH ->
        "Recorded data can't be recovered. It takes 5–10 minutes: the LED turns blue, then red with a jingle " +
            "when done. Don't power the board off. The app will probably lose the connection meanwhile."
    BoardCommand.TX_LOCK ->
        "It prepares the flash for the flight log, then turns Bluetooth off: the LED turns purple, then green. " +
            "The app loses the board until it restarts."
    BoardCommand.REBOOT -> "It restarts, and the app loses the connection. Reconnect when it shows up again."
    BoardCommand.AUX_ON, BoardCommand.AUX_OFF -> ""
}
