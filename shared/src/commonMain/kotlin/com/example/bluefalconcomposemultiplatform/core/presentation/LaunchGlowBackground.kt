package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowHorizon
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowSkyLow
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowSkyMid
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowSkyTop

// Night sky with stars, warming into a glow at the bottom like a rocket lighting up on the pad
@Composable
fun LaunchGlowBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to GlowSkyTop,
                0.5f to GlowSkyMid,
                0.75f to GlowSkyLow,
                1f to GlowHorizon
            )
        )

        stars.forEach { star ->
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.radiusDp.dp.toPx(),
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }

        // An ellipse 85% of the width and 42% of the height, centred just below the bottom edge:
        // draw a circle with radius = the ellipse's width and squash it vertically
        val radiusX = size.width * 0.85f
        val radiusY = size.height * 0.42f
        val center = Offset(size.width / 2f, size.height * 1.04f)
        scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xD9FFAA28),
                    0.25f to Color(0x8CFF6E1E),
                    0.55f to Color(0x668C1D40),
                    0.78f to Color.Transparent,
                    center = center,
                    radius = radiusX
                ),
                radius = radiusX,
                center = center
            )
        }
    }
}

private class Star(val x: Float, val y: Float, val radiusDp: Float, val alpha: Float)

// Fixed positions (fractions of the screen) so the sky looks the same every time
private val stars = listOf(
    Star(0.072f, 0.042f, 0.9f, 0.8f), Star(0.208f, 0.123f, 0.6f, 0.5f), Star(0.364f, 0.035f, 1.1f, 0.9f),
    Star(0.548f, 0.079f, 0.6f, 0.45f), Star(0.704f, 0.029f, 0.8f, 0.7f), Star(0.856f, 0.100f, 1.0f, 0.85f),
    Star(0.944f, 0.185f, 0.6f, 0.5f), Star(0.048f, 0.227f, 0.7f, 0.6f), Star(0.912f, 0.308f, 0.9f, 0.7f),
    Star(0.124f, 0.365f, 0.5f, 0.4f), Star(0.960f, 0.446f, 0.6f, 0.45f), Star(0.032f, 0.504f, 0.9f, 0.6f),
    Star(0.248f, 0.246f, 0.5f, 0.35f), Star(0.784f, 0.227f, 0.5f, 0.4f), Star(0.640f, 0.169f, 0.7f, 0.55f),
    Star(0.280f, 0.577f, 0.6f, 0.35f), Star(0.888f, 0.612f, 0.8f, 0.5f), Star(0.080f, 0.677f, 0.6f, 0.4f),
    Star(0.480f, 0.015f, 0.5f, 0.5f), Star(0.976f, 0.019f, 0.6f, 0.6f), Star(0.176f, 0.185f, 1.2f, 0.9f),
    Star(0.820f, 0.146f, 0.5f, 0.4f), Star(0.440f, 0.185f, 0.4f, 0.35f), Star(0.944f, 0.750f, 0.6f, 0.35f),
    Star(0.056f, 0.827f, 0.7f, 0.3f), Star(0.640f, 0.481f, 0.5f, 0.25f), Star(0.384f, 0.788f, 0.6f, 0.25f)
)
