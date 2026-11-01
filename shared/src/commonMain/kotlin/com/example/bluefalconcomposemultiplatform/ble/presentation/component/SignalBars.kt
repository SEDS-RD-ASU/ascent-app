package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack

// Four bars of increasing height; the first `bars` are lit
@Composable
fun SignalBars(bars: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(5.dp, 8.dp, 11.dp, 14.dp).forEachIndexed { index, barHeight ->
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(barHeight)
                    .background(if (index < bars) AsuGold else SignalTrack, RoundedCornerShape(1.dp))
            )
        }
    }
}
