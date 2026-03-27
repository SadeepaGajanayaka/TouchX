package com.companymade.touchx.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.companymade.touchx.viewmodel.GestureMode
import com.companymade.touchx.viewmodel.GestureType
import com.companymade.touchx.viewmodel.PasswordGesture
import kotlin.math.sqrt

@Composable
fun SetPasswordScreen(
    imageUri: Uri,
    gestureMode: GestureMode,
    targetCount: Int,
    onPasswordSet: (Uri, List<PasswordGesture>) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(imageUri) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use { 
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun getAdaptiveColor(x: Float, y: Float): Color {
        val b = bitmap ?: return Color(0xFF00E5FF)
        val cw = containerSize.width.toFloat()
        val ch = containerSize.height.toFloat()
        val bw = b.width.toFloat()
        val bh = b.height.toFloat()
        if (cw <= 0 || ch <= 0 || bw <= 0 || bh <= 0) return Color.Black
        
        // ContentScale.Crop Math
        val scale = maxOf(cw / bw, ch / bh)
        val contentW = bw * scale
        val contentH = bh * scale
        val diffX = (contentW - cw) / 2f
        val diffY = (contentH - ch) / 2f
        
        val bitmapPixelX = ((x * cw + diffX) / scale).toInt().coerceIn(0, b.width - 1)
        val bitmapPixelY = ((y * ch + diffY) / scale).toInt().coerceIn(0, b.height - 1)
        
        val pixel = b.getPixel(bitmapPixelX, bitmapPixelY)
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val bl = android.graphics.Color.blue(pixel)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * bl) / 255.0
        
        // "opposite way" fix: user said light -> black, dark -> white.
        return if (luminance > 0.5) Color.Black else Color.White
    }

    var gestures by remember { mutableStateOf(listOf<PasswordGesture>()) }
    
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    
    var showSuccessAlert by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = rememberAsyncImagePainter(imageUri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { containerSize = it.size }
                .pointerInput(gestures.size, gestureMode) {
                    if (gestures.size < targetCount) {
                        awaitPointerEventScope {
                            val down = awaitFirstDown()
                            dragStart = down.position
                            dragCurrent = down.position
                            
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                
                                if (change.changedToUp()) {
                                    val s = dragStart ?: return@awaitPointerEventScope
                                    val e = if (gestureMode == GestureMode.TAPS_ONLY) s else (dragCurrent ?: s)
                                    
                                    val dx = e.x - s.x
                                    val dy = e.y - s.y
                                    val distance = sqrt(dx*dx + dy*dy)
                                    
                                    val type = if (distance < 50f || gestureMode == GestureMode.TAPS_ONLY) GestureType.TAP else GestureType.LINE
                                    
                                    val newGesture = PasswordGesture(
                                        type = type,
                                        xStart = s.x / containerSize.width,
                                        yStart = s.y / containerSize.height,
                                        xEnd = if (type == GestureType.TAP) s.x / containerSize.width else e.x / containerSize.width,
                                        yEnd = if (type == GestureType.TAP) s.y / containerSize.height else e.y / containerSize.height
                                    )
                                    
                                    gestures = gestures + newGesture
                                    dragStart = null
                                    dragCurrent = null
                                    break
                                } else {
                                    if (gestureMode == GestureMode.FREEHAND) {
                                        dragCurrent = change.position
                                    }
                                    if (event.type == PointerEventType.Move) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                },
            contentScale = ContentScale.Crop
        )

        // GESTURE MARKS - PREMIUM ADAPTIVE GLOW
        Canvas(modifier = Modifier.fillMaxSize()) {
            gestures.forEach { g ->
                val adaptiveColor = getAdaptiveColor(g.xStart, g.yStart)
                val glowColor = adaptiveColor.copy(alpha = 0.4f)
                val start = Offset(g.xStart * size.width, g.yStart * size.height)
                val end = Offset(g.xEnd * size.width, g.yEnd * size.height)

                if (g.type == GestureType.TAP) {
                    // Outer Soft Glow
                    drawCircle(
                        color = adaptiveColor.copy(alpha = 0.2f),
                        center = start,
                        radius = 40f
                    )
                    // Medium Ring
                    drawCircle(
                        color = adaptiveColor.copy(alpha = 0.4f),
                        center = start,
                        radius = 28f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                    )
                    // High-Intensity Core
                    drawCircle(
                        color = adaptiveColor,
                        center = start,
                        radius = 14f
                    )
                } else {
                    val energyBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(adaptiveColor, adaptiveColor.copy(alpha = 0.4f)),
                        start = start,
                        end = end
                    )
                    // Outer soft glow line
                    drawLine(
                        color = adaptiveColor.copy(alpha = 0.15f),
                        start = start,
                        end = end,
                        strokeWidth = 30f,
                        cap = StrokeCap.Round
                    )
                    // Main energy line with gradient
                    drawLine(
                        brush = energyBrush,
                        start = start,
                        end = end,
                        strokeWidth = 12f,
                        cap = StrokeCap.Round
                    )
                    // Precise core line
                    drawLine(
                        color = adaptiveColor.copy(alpha = 0.9f),
                        start = start,
                        end = end,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    
                    // Cap circles for the line
                    drawCircle(adaptiveColor, radius = 8f, center = start)
                    drawCircle(adaptiveColor, radius = 8f, center = end)
                }
            }
        }

        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = Color.White)
            }
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // FOOTER - Dynamic visibility
        if (gestures.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Gesture ${gestures.size} of $targetCount",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { gestures = emptyList() },
                        modifier = Modifier.weight(1f).height(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB0B0B0), contentColor = Color.Black)
                    ) {
                        Text("Clear", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Button(
                        onClick = { 
                            if (gestures.size == targetCount) {
                                showSuccessAlert = true
                            }
                        },
                        enabled = gestures.size >= 1,
                        modifier = Modifier.weight(1f).height(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gestures.size == targetCount) Color.White else Color(0xFFB0B0B0), 
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = if (gestures.size == targetCount) "Finish" else "Next",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // PREMIUM SUCCESS OVERLAY
        if (showSuccessAlert) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(32.dp))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White, RoundedCornerShape(40.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = Color.Black, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Password Set!",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Your new Picture Password is ready to protect your device.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { 
                            showSuccessAlert = false
                            onPasswordSet(imageUri, gestures)
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Finish", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
