package com.voicechat.android.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RecordingIndicator(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.error
) {
    if (isRecording) {
        val infiniteTransition = rememberInfiniteTransition(label = "recording")
        
        val animatedAmplitudes = remember {
            List(5) { Animatable(0.3f) }
        }
        
        val phase = remember {
            Animatable(0f)
        }
        
        // Animate each bar with different phases
        val animation = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
        
        Canvas(
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
        ) {
            val barWidth = 6.dp.toPx()
            val barSpacing = 8.dp.toPx()
            val centerY = size.height / 2
            val startX = (size.width - (5 * barWidth + 4 * barSpacing)) / 2
            
            for (i in 0 until 5) {
                val angle = animation.value + (i * 45f)
                val radians = Math.toRadians(angle.toDouble())
                val amplitude = 0.3f + (0.7f * kotlin.math.abs(kotlin.math.sin(radians))).toFloat()
                val height = 8.dp.toPx() + (amplitude * 16.dp.toPx())
                
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(
                        startX + i * (barWidth + barSpacing),
                        centerY - height / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun WaveformVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Canvas(
        modifier = modifier
            .size(width = 200.dp, height = 40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
    ) {
        val barWidth = 4.dp.toPx()
        val barSpacing = 3.dp.toPx()
        val totalWidth = 5 * barWidth + 4 * barSpacing
        val startX = (size.width - totalWidth) / 2
        val centerY = size.height / 2
        
        amplitudes.forEachIndexed { index, amplitude ->
            val maxHeight = size.height * 0.8f
            val height = amplitude * maxHeight
            val x = startX + index * (barWidth + barSpacing)
            
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - height / 2),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}
