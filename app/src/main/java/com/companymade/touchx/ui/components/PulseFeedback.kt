package com.companymade.touchx.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PulseFeedback(
    x: Float,
    y: Float,
    color: Color,
    onAnimationEnd: () -> Unit
) {
    val alphaAnim = remember { Animatable(0.4f) }
    val sizeAnim = remember { Animatable(20f) }
    
    LaunchedEffect(Unit) {
        launch {
            sizeAnim.animateTo(
                targetValue = 100f,
                animationSpec = tween(300, easing = LinearOutSlowInEasing)
            )
        }
        alphaAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = LinearOutSlowInEasing)
        )
        onAnimationEnd()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(x, y)
        val pulseColor = if (color == Color.Red) Color.Red else Color(0xFF00E5FF)
        val currentAlpha = alphaAnim.value
        val currentSize = sizeAnim.value
        
        // 1. OUTER SUBTLE RING
        drawCircle(
            color = pulseColor.copy(alpha = currentAlpha * 0.5f),
            radius = currentSize,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // 2. SOFT CENTER GLOW
        drawCircle(
            color = pulseColor.copy(alpha = currentAlpha),
            radius = currentSize * 0.5f,
            center = center
        )

        // 3. MINIMAL CORE
        drawCircle(
            color = Color.White.copy(alpha = currentAlpha),
            radius = 10f,
            center = center
        )
    }
}
