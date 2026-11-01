package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ble.presentation.HOLD_TO_CONFIRM
import com.example.bluefalconcomposemultiplatform.ui.theme.HoldFill
import com.example.bluefalconcomposemultiplatform.ui.theme.HoldTrack
import kotlinx.coroutines.launch

// A button that acts only once it's been held for HOLD_TO_CONFIRM. While it's held, a brighter fill grows
// across it; letting go (or sliding off) before it's full empties it, and nothing happens.
@Composable
fun HoldToConfirmButton(
    label: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    // How far the hold has got, from 0 to 1
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val confirm by rememberUpdatedState(onConfirmed)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HoldTrack)
            .drawBehind { drawRect(HoldFill, size = size.copy(width = size.width * progress.value)) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val hold = scope.launch {
                        progress.animateTo(
                            1f,
                            tween(HOLD_TO_CONFIRM.inWholeMilliseconds.toInt(), easing = LinearEasing)
                        )
                        confirm()
                    }
                    waitForUpOrCancellation()
                    if (hold.isActive) {
                        hold.cancel()
                        scope.launch { progress.snapTo(0f) }
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
