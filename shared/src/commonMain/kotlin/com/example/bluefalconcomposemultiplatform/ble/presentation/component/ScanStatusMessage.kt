package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ble.presentation.ScanMessage
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.OnAsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary

@Composable
fun ScanStatusMessage(
    message: ScanMessage,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Problems the user has to fix get a gold border and a "!" instead of a spinner
    val problem = message == ScanMessage.BluetoothOff ||
        message == ScanMessage.NoPermission ||
        message == ScanMessage.Unsupported
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBackground)
            .border(1.dp, if (problem) AsuGold.copy(alpha = 0.45f) else CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (problem) {
                // An icon rather than Text("!"): the theme's 24sp line height pushes a small "!" out of an 18dp circle
                Icon(Icons.Filled.Error, contentDescription = null, tint = AsuGold, modifier = Modifier.size(20.dp))
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AsuGold,
                    trackColor = SignalTrack,
                    strokeWidth = 2.dp
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(title(message), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        when (message) {
            ScanMessage.NoPermission -> {
                Text(
                    "Turn on Bluetooth for this app in Settings to find your ASCENT.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = AsuGold, contentColor = OnAsuGold),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            }
            ScanMessage.NotFound -> {
                Column(Modifier.padding(top = 6.dp)) {
                    Text("•  Make sure the ASCENT is powered on", color = TextSecondary, fontSize = 13.sp)
                    Text("•  Bring your phone closer to the board", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        "Still searching…",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            else -> {}
        }
    }
}

private fun title(message: ScanMessage): String = when (message) {
    ScanMessage.BluetoothOff -> "Bluetooth is off. Turn it on to find your ASCENT."
    ScanMessage.NoPermission -> "GOOBER Connect isn't allowed to use Bluetooth"
    ScanMessage.Unsupported -> "This device doesn't support Bluetooth LE."
    ScanMessage.StartingBluetooth -> "Starting Bluetooth…"
    ScanMessage.Searching -> "Searching for ASCENT…"
    ScanMessage.NotFound -> "No ASCENT found yet"
}
