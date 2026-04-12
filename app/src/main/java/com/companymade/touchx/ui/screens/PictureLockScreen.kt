package com.companymade.touchx.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.companymade.touchx.viewmodel.ClockStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

data class TrailPoint(val offset: Offset, val timestamp: Long)

@Composable
fun PictureLockScreen(
    imageUri: Uri,
    gestures: List<PasswordGesture>,
    gestureColor: Int,
    clockStyle: ClockStyle,
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
    
    var isUnlocking by remember { mutableStateOf(false) }
    val unlockAlpha by animateFloatAsState(
        targetValue = if (isUnlocking) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "UnlockAlpha",
        finishedListener = { alphaValue -> if (alphaValue == 0f) onUnlock() }
    )
    
    val initialNow = java.util.Calendar.getInstance().time
    val timeFormatRef = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val dateFormatRef = remember { java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()) }

    var timeText by remember { mutableStateOf(timeFormatRef.format(initialNow)) }
    var dateText by remember { mutableStateOf(dateFormatRef.format(initialNow)) }
    
    var unreadSmsCount by remember { mutableIntStateOf(0) }
    var missedCallCount by remember { mutableIntStateOf(0) }
    var hasGeneralNotifications by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val touchColor = Color(gestureColor)

    LaunchedEffect(Unit) {
        while(true) {
            val now = java.util.Calendar.getInstance().time
            timeText = timeFormatRef.format(now)
            dateText = dateFormatRef.format(now)
            
            // REFRESH COUNTS
            try {
                val smsCursor = context.contentResolver.query(
                    android.net.Uri.parse("content://sms/inbox"),
                    arrayOf("_id"), "read = 0", null, null
                )
                unreadSmsCount = smsCursor?.count ?: 0
                smsCursor?.close()
                
                val callCursor = context.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(android.provider.CallLog.Calls._ID),
                    "${android.provider.CallLog.Calls.TYPE} = ${android.provider.CallLog.Calls.MISSED_TYPE} AND ${android.provider.CallLog.Calls.IS_READ} = 0",
                    null, null
                )
                missedCallCount = callCursor?.count ?: 0
                callCursor?.close()
                
                // CHECK GENERAL NOTIFICATIONS
                hasGeneralNotifications = com.companymade.touchx.NotificationService.isNotificationActive
            } catch (e: Exception) { e.printStackTrace() }
            
            delay(10000) // Update every 10 seconds
        }
    }
    
    val scope = rememberCoroutineScope()
    
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
    
    Box(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = unlockAlpha }
        .background(Color.Black)
    ) {
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
                            tapPos = down.position.x to down.position.y
                            showPulse = true
                            isCorrectGesture = true // Default to true while holding, validation happens on release
                            
                            dragStart = down.position
                            dragCurrent = down.position
                            isError = false
                            
                            trailPoints.add(TrailPoint(down.position, System.currentTimeMillis()))
                            
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
                                    
                                    // Validation happens here. If false, we'll show an error pulse next time or feedback
                                    
                                    if (startXOk && startYOk && endOk) {
                                        isCorrectGesture = true
                                        isError = false
                                        if (currentStep == gestures.size - 1) {
                                            scope.launch {
                                                delay(400)
                                                isUnlocking = true
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
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = System.currentTimeMillis()
            trailPoints.forEach { pt ->
                val age = now - pt.timestamp
                val alpha = (1.0f - age / 300f).coerceIn(0f, 1f)
                
                drawCircle(
                    color = touchColor.copy(alpha = alpha * 0.4f),
                    radius = 12f,
                    center = pt.offset
                )
            }
        }

        tapPos?.let { (x, y) ->
            if (showPulse) {
                PulseFeedback(x = x, y = y, color = if (isCorrectGesture) touchColor else Color.Red) {
                    showPulse = false
                }
            }
        }

        // 1. THEMED CLOCK & DATE WIDGET
        val widgetColor = getAdaptiveColor(0.5f, 0.15f)
        val isLightTheme = widgetColor == Color.Black

        when (clockStyle) {
            ClockStyle.CLASSIC -> {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(timeText, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraLight, fontSize = 80.sp), color = widgetColor)
                    Text(dateText, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = widgetColor.copy(alpha = 0.7f))
                    Spacer(Modifier.height(24.dp))
                    NotificationRow(missedCallCount, unreadSmsCount, hasGeneralNotifications, widgetColor)
                }
            }
            ClockStyle.MODERN -> {
                val timeParts = timeText.split(":")
                val hours = timeParts.getOrNull(0) ?: "--"
                val minutes = timeParts.getOrNull(1) ?: "--"
                
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 80.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(hours, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 90.sp, lineHeight = 80.sp), color = widgetColor)
                        Text(minutes, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 90.sp, lineHeight = 80.sp), color = widgetColor)
                    }
                    Box(modifier = Modifier.padding(start = 16.dp).width(1.dp).height(120.dp).background(widgetColor.copy(alpha = 0.2f)))
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(dateText.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp), color = widgetColor)
                        Spacer(Modifier.height(12.dp))
                        NotificationRow(missedCallCount, unreadSmsCount, hasGeneralNotifications, widgetColor)
                    }
                }
            }
            ClockStyle.MINIMAL -> {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 60.dp)
                ) {
                    Text(timeText, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 42.sp), color = widgetColor)
                    Text(dateText, style = MaterialTheme.typography.bodyMedium, color = widgetColor.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))
                    NotificationRow(missedCallCount, unreadSmsCount, hasGeneralNotifications, widgetColor)
                }
            }
            ClockStyle.ELEGANT -> {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(timeText, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.W100, fontSize = 100.sp, letterSpacing = 8.sp), color = widgetColor)
                    Text(dateText.uppercase(), style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Light), color = widgetColor)
                    Spacer(Modifier.height(32.dp))
                    NotificationRow(missedCallCount, unreadSmsCount, hasGeneralNotifications, widgetColor)
                }
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

@Composable
fun NotificationRow(
    missedCallCount: Int,
    unreadSmsCount: Int,
    hasGeneralNotifications: Boolean,
    widgetColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Phone / Missed Calls
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = widgetColor.copy(alpha = if (missedCallCount > 0) 1f else 0.35f)
            )
            if (missedCallCount > 0) {
                Text(
                    text = " $missedCallCount",
                    color = widgetColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        
        // Mail / Unread SMS
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Mail,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = widgetColor.copy(alpha = if (unreadSmsCount > 0) 1f else 0.35f)
            )
            if (unreadSmsCount > 0) {
                Text(
                    text = " $unreadSmsCount",
                    color = widgetColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // General Notifications
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = widgetColor.copy(alpha = if (hasGeneralNotifications) 1f else 0.35f)
        )
    }
}
