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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlin.math.sqrt

@Composable
fun SetPasswordScreen(
    imageUri: Uri,
    gestureMode: GestureMode,
    targetCount: Int,
    gestureColor: Int,
    onPasswordSet: (Uri, List<PasswordGesture>) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val touchColor = Color(gestureColor)
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

    var gestures by remember { mutableStateOf(listOf<PasswordGesture>()) }
    
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    
    var showSuccessAlert by remember { mutableStateOf(false) }
    
    // ANIMATION & FLOW STATES
    var isReviewing by remember { mutableIntStateOf(-1) } // -1 = input, 0..N = reviewing index
    val reviewAnimProgress = remember { Animatable(0f) }
    var pendingGesture by remember { mutableStateOf<PasswordGesture?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = rememberAsyncImagePainter(imageUri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { containerSize = it.size }
                .pointerInput(gestures.size, gestureMode) {
                    if (gestures.size < targetCount) {
                        awaitPointerEventScope {
                            while (true) {
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
                                        
                                        pendingGesture = newGesture
                                        dragStart = null
                                        dragCurrent = null
                                        break // Exit movement loop, back to FirstDown
                                    } else {
                                        if (gestureMode == GestureMode.LINE) {
                                            dragCurrent = change.position
                                        }
                                        if (event.type == PointerEventType.Move) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            contentScale = ContentScale.Crop
        )

        // GESTURE MARKS - DYNAMIC REVEAL
        Canvas(modifier = Modifier.fillMaxSize()) {
            val toDraw = if (isReviewing != -1) {
                gestures.getOrNull(isReviewing)?.let { listOf(it) } ?: emptyList()
            } else {
                pendingGesture?.let { listOf(it) } ?: emptyList()
            }
            
            toDraw.forEach { g ->
                val start = Offset(g.xStart * size.width, g.yStart * size.height)
                val end = Offset(g.xEnd * size.width, g.yEnd * size.height)
                
                val progress = if (isReviewing != -1) reviewAnimProgress.value else 1f
                val animatedEnd = Offset(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress
                )

                if (g.type == GestureType.TAP) {
                    val tapAlpha = if (isReviewing != -1) progress else 1f
                    drawCircle(color = touchColor.copy(alpha = 0.2f * tapAlpha), center = start, radius = 40f)
                    drawCircle(color = touchColor.copy(alpha = 0.4f * tapAlpha), center = start, radius = 28f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                    drawCircle(color = touchColor.copy(alpha = tapAlpha), center = start, radius = 14f)
                } else {
                    val energyBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(touchColor, touchColor.copy(alpha = 0.4f)),
                        start = start, end = animatedEnd
                    )
                    drawLine(color = touchColor.copy(alpha = 0.15f), start = start, end = animatedEnd, strokeWidth = 30f, cap = StrokeCap.Round)
                    drawLine(brush = energyBrush, start = start, end = animatedEnd, strokeWidth = 12f, cap = StrokeCap.Round)
                    drawLine(color = touchColor.copy(alpha = 0.9f), start = start, end = animatedEnd, strokeWidth = 3f, cap = StrokeCap.Round)
                    
                    drawCircle(touchColor, radius = 8f, center = start)
                    if (progress > 0.95f) drawCircle(touchColor, radius = 8f, center = end)
                }
            }
            
            // Also draw current drag
            dragStart?.let { s ->
                val e = dragCurrent ?: s
                if (gestureMode == GestureMode.LINE) {
                    drawLine(color = touchColor.copy(alpha = 0.5f), start = s, end = e, strokeWidth = 8f, cap = StrokeCap.Round)
                }
                drawCircle(color = touchColor.copy(alpha = 0.5f), center = s, radius = 20f)
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

        // FOOTER - Always visible for guidance
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PROGRESS DOTS
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(targetCount) { index ->
                    val isDone = index < gestures.size
                    val isCurrent = index == gestures.size
                    val dotColor = if (isDone) Color.White else if (isCurrent && pendingGesture != null) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
                    val dotSize = if (isCurrent) 12.dp else 8.dp
                    
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(RoundedCornerShape(6.dp))
                            .background(dotColor)
                    )
                }
            }

            Text(
                text = when {
                    isReviewing != -1 -> "Reviewing..."
                    pendingGesture != null -> "Confirm this gesture"
                    gestures.size < targetCount -> "Draw gesture ${gestures.size + 1}"
                    else -> "All gestures added"
                },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // CLEAR / UNDO BUTTON
                Button(
                    onClick = { 
                        if (pendingGesture != null) {
                            pendingGesture = null
                        } else if (gestures.isNotEmpty()) {
                            gestures = gestures.dropLast(1)
                        }
                    },
                    enabled = (pendingGesture != null || gestures.isNotEmpty()) && isReviewing == -1,
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f), 
                        contentColor = Color.White
                    )
                ) {
                    Text(if (pendingGesture != null) "Clear" else "Undo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                
                // NEXT / FINISH BUTTON
                Button(
                    onClick = { 
                        val pg = pendingGesture ?: return@Button
                        val newList = gestures + pg
                        gestures = newList
                        pendingGesture = null
                        
                        if (newList.size == targetCount) {
                            scope.launch {
                                // Short delay to let user see the final confirmation
                                delay(300)
                                for (i in newList.indices) {
                                    isReviewing = i
                                    reviewAnimProgress.snapTo(0f)
                                    reviewAnimProgress.animateTo(1f, tween(600))
                                    delay(200)
                                }
                                isReviewing = -1
                                showSuccessAlert = true
                            }
                        }
                    },
                    enabled = pendingGesture != null && isReviewing == -1,
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingGesture != null) Color.White else Color.White.copy(alpha = 0.1f), 
                        contentColor = if (pendingGesture != null) Color.Black else Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    val isFinal = gestures.size + 1 == targetCount
                    Text(
                        text = if (isFinal) "Finish" else "Next",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
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
