package com.companymade.touchx.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.companymade.touchx.ui.components.PulseFeedback
import com.companymade.touchx.viewmodel.GestureType
import com.companymade.touchx.viewmodel.PasswordGesture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

data class TrailPoint(val offset: Offset, val timestamp: Long)

@Composable
fun PictureLockScreen(
    imageUri: Uri,
    gestures: List<PasswordGesture>,
    onUnlock: () -> Unit
) {
    if (gestures.isEmpty()) {
        // Fallback for unexpected empty gestures
        onUnlock()
        return
    }

    var currentStep by remember { mutableIntStateOf(0) }
    var tapPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var showPulse by remember { mutableStateOf(false) }
    var isCorrectGesture by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isError by remember { mutableStateOf(false) }
    
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val dateFormat = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
        while(true) {
            val now = java.util.Calendar.getInstance().time
            timeText = timeFormat.format(now)
            dateText = dateFormat.format(now)
            delay(10000) // Update every 10 seconds
        }
    }
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val trailPoints = remember { mutableStateListOf<TrailPoint>() }
    
    LaunchedEffect(Unit) {
        while(true) {
            delay(50)
            val now = System.currentTimeMillis()
            trailPoints.removeAll { now - it.timestamp > 300 }
        }
    }

    val bitmap = remember(imageUri) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use { 
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

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
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Lock",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { containerSize = it.size }
                .pointerInput(gestures) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            dragStart = down.position
                            dragCurrent = down.position
                            isError = false
                            
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                
                                if (change.changedToUp()) {
                                    val s = dragStart ?: return@awaitPointerEventScope
                                    val e = dragCurrent ?: s
                                    
                                    val dx = e.x - s.x
                                    val dy = e.y - s.y
                                    val dist = sqrt(dx*dx + dy*dy)
                                    val inputType = if (dist < 50f) GestureType.TAP else GestureType.LINE
                                    
                                    val target = gestures[currentStep]
                                    val width = containerSize.width.toFloat().coerceAtLeast(1f)
                                    val height = containerSize.height.toFloat().coerceAtLeast(1f)
                                    
                                    val startXOk = abs(s.x / width - target.xStart) < 0.10f
                                    val startYOk = abs(s.y / height - target.yStart) < 0.10f
                                    
                                    val endOk = if (target.type == GestureType.TAP) {
                                        inputType == GestureType.TAP
                                    } else {
                                        val endXOk = abs(e.x / width - target.xEnd) < 0.10f
                                        val endYOk = abs(e.y / height - target.yEnd) < 0.10f
                                        endXOk && endYOk && inputType == GestureType.LINE
                                    }
                                    
                                    tapPos = s.x to s.y
                                    showPulse = true
                                    
                                    if (startXOk && startYOk && endOk) {
                                        isCorrectGesture = true
                                        isError = false
                                        if (currentStep == gestures.size - 1) {
                                            scope.launch {
                                                delay(400)
                                                onUnlock()
                                            }
                                        } else {
                                            currentStep++
                                        }
                                    } else {
                                        isCorrectGesture = false
                                        isError = true
                                        currentStep = 0
                                    }
                                    
                                    dragStart = null
                                    dragCurrent = null
                                    break
                                } else {
                                    dragCurrent = change.position
                                    trailPoints.add(TrailPoint(change.position, System.currentTimeMillis()))
                                    if (event.type == PointerEventType.Move) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                }
        )
        
        // STEALTH MODE: Drawing feedback is now handled only by PulseFeedback and the Dynamic Trail.
        // The persistent line drawing Canvas has been removed for security.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = System.currentTimeMillis()
            trailPoints.forEach { pt ->
                val age = now - pt.timestamp
                val alpha = (1.0f - age / 300f).coerceIn(0f, 1f)
                val color = getAdaptiveColor(pt.offset.x / size.width, pt.offset.y / size.height)
                
                drawCircle(
                    color = color.copy(alpha = alpha * 0.4f),
                    radius = 12f,
                    center = pt.offset
                )
            }
        }

        tapPos?.let { (x, y) ->
            if (showPulse) {
                val adaptiveTapColor = getAdaptiveColor(x / containerSize.width, y / containerSize.height)
                PulseFeedback(x = x, y = y, color = if (isCorrectGesture) adaptiveTapColor else Color.Red) {
                    showPulse = false
                }
            }
        }

        // 1. PREMIUM CLOCK & DATE WIDGET (ADAPTIVE GLASSMORPHISM)
        val widgetColor = getAdaptiveColor(0.5f, 0.15f)
        val isLightTheme = widgetColor == Color.Black
        
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f))
                .border(
                    0.5.dp, 
                    if (isLightTheme) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.15f), 
                    RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraLight,
                    fontSize = 80.sp,
                    letterSpacing = (-2).sp
                ),
                color = widgetColor
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                ),
                color = widgetColor.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Subtle Notification Icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = widgetColor.copy(alpha = 0.35f)
                )
                Icon(
                    Icons.Default.Mail,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = widgetColor.copy(alpha = 0.35f)
                )
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = widgetColor.copy(alpha = 0.35f)
                )
            }
        }

        // 2. INCORRECT PASSWORD MESSAGE (REFINED MICRO-COPY)
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Incorrect Password",
                    color = Color.Red.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Subtle underline for error
                Box(modifier = Modifier.width(40.dp).height(1.dp).background(Color.Red.copy(alpha = 0.4f)))
            }
        }
    }
}
