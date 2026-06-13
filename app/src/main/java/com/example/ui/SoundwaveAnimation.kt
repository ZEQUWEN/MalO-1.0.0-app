package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun SoundwaveAnimation(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // We animate a single phase from 0 to 2PI continuously when playing
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // Static points for visual variance
    val amplitudes = remember { List(30) { Random.nextFloat() * 0.8f + 0.2f } }

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(40.dp)) {
        val barWidth = 6.dp.toPx()
        val space = 4.dp.toPx()
        val numBars = (size.width / (barWidth + space)).toInt()
        val centerY = size.height / 2

        for (i in 0 until numBars) {
            val baseAmp = amplitudes[i % amplitudes.size]
            val heightMultiplier = if (isPlaying) {
                // Sine wave pattern shifting by phase
                val shift = (i.toFloat() / numBars) * 2 * Math.PI.toFloat()
                val sine = (Math.sin((shift + phase).toDouble()).toFloat() + 1f) / 2f
                baseAmp * (0.3f + 0.7f * sine)
            } else {
                baseAmp * 0.1f // Flat when stopped
            }
            
            val barHeight = size.height * heightMultiplier
            
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + space), centerY - barHeight / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
