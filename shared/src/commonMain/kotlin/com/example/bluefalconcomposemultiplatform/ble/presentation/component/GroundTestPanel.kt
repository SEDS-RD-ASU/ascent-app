package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ble.presentation.BoardCommand
import com.example.bluefalconcomposemultiplatform.ble.presentation.PopContinuity
import com.example.bluefalconcomposemultiplatform.ble.presentation.formatTimeLeft
import com.example.bluefalconcomposemultiplatform.ui.theme.CheckGreenText
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandRed
import com.example.bluefalconcomposemultiplatform.ui.theme.GroundTestBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.GroundTestBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.PlainButton
import com.example.bluefalconcomposemultiplatform.ui.theme.ProblemRedText
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary
import kotlin.time.Duration

// The ground-test box: the arm switch with its countdown, the two pops, and each pop's continuity.
// The pops can't be tapped until the board is armed.
@Composable
fun GroundTestPanel(
    armedTimeLeft: Duration?,
    apogeeContinuity: PopContinuity,
    mainsContinuity: PopContinuity,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onPop: (BoardCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val armed = armedTimeLeft != null
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GroundTestBackground)
            .border(1.dp, GroundTestBorder, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "GROUND TEST",
                color = ProblemRedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (armedTimeLeft != null) "Armed · ${formatTimeLeft(armedTimeLeft)} left" else "Not armed",
                color = if (armed) ProblemRedText else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Switch(
                checked = armed,
                onCheckedChange = { if (it) onArm() else onDisarm() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CommandRed,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SignalTrack,
                    uncheckedBorderColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(10.dp))
            Text("Arm pyro", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PopColumn(BoardCommand.POP_APOGEE, apogeeContinuity, armed, onPop, Modifier.weight(1f))
            PopColumn(BoardCommand.POP_MAINS, mainsContinuity, armed, onPop, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PopColumn(
    command: BoardCommand,
    continuity: PopContinuity,
    armed: Boolean,
    onPop: (BoardCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { onPop(command) },
            enabled = armed,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CommandRed,
                contentColor = Color.White,
                disabledContainerColor = PlainButton,
                disabledContentColor = TextSecondary.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(command.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        ContinuityLine(continuity, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ContinuityLine(continuity: PopContinuity, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (continuity) {
            PopContinuity.Yes -> {
                Icon(Icons.Filled.Check, contentDescription = null, tint = CheckGreenText, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("Continuity", color = CheckGreenText, fontSize = 12.sp)
            }
            PopContinuity.No -> {
                Icon(Icons.Filled.Close, contentDescription = null, tint = ProblemRedText, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("No continuity", color = ProblemRedText, fontSize = 12.sp)
            }
            PopContinuity.NoTelemetry -> Text("No telemetry", color = TextSecondary, fontSize = 12.sp)
        }
    }
}
