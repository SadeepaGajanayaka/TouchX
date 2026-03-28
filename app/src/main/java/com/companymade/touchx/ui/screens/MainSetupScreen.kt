package com.companymade.touchx.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File
import com.companymade.touchx.viewmodel.GestureMode

@Composable
fun MainSetupScreen(
    hasPassword: Boolean,
    hasOverlayPermission: Boolean,
    isBatteryOptimized: Boolean,
    onSetNewPassword: (Uri, GestureMode, Int) -> Unit,
    onLock: () -> Unit,
    onRemovePassword: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestAutoStart: () -> Unit
) {
    var showInstructions by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = Color.White
                )
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF222222))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Help", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Help, contentDescription = null, tint = Color.White) },
                            onClick = {
                                showMenu = false
                                showHelp = true
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardCard(
                    icon = Icons.Default.Layers,
                    title = "Overlay Permission",
                    subtitle = "Required for lock screen",
                    isToggled = hasOverlayPermission,
                    onToggle = onRequestOverlay
                )
                DashboardCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Battery Optimization",
                    subtitle = "Must be disabled",
                    isToggled = !isBatteryOptimized,
                    onToggle = onRequestBattery
                )
                DashboardCard(
                    icon = Icons.Default.RocketLaunch,
                    title = "Auto-Start",
                    subtitle = "Run on boot",
                    isToggled = false,
                    onToggle = onRequestAutoStart
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { showInstructions = true },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF222222),
                    contentColor = Color.White
                )
            ) {
                Text(if (hasPassword) "Update Picture Password" else "Setup Picture Password", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLock,
                enabled = hasPassword,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasPassword) Color.White else Color(0xFF1A1A1A),
                    contentColor = if (hasPassword) Color.Black else Color.Gray
                )
            ) {
                Text("Lock Now", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

            if (hasPassword) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onRemovePassword,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Remove Password", color = Color.Red.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showInstructions) {
            InstructionsDialog(
                onClose = { showInstructions = false },
                onNext = { 
                    showInstructions = false
                    showUploadDialog = true
                }
            )
        }

        if (showUploadDialog) {
            UploadDialog(
                onClose = { showUploadDialog = false },
                onFinalNext = { uri, mode, count ->
                    showUploadDialog = false
                    onSetNewPassword(uri, mode, count)
                }
            )
        }

        if (showHelp) {
            HelpDialog(onClose = { showHelp = false })
        }
    }
}

@Composable
fun HelpDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFB0B0B0)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("TouchX Help", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HelpItem("Permissions", "Ensure all three dashboard toggles are ON (White) for full stability.")
                        HelpItem("Screen Overlay", "If the lock screen doesn't show, re-verify the 'Display over other apps' permission.")
                        HelpItem("Battery", "Set battery optimization to 'Unrestricted' so Android doesn't stop your protection.")
                        HelpItem("Gestures", "Use high-contrast features in your photos (like eyes, buttons, or edges) to help you remember your password.")
                        HelpItem("⚠️ Honor / Huawei / Xiaomi", "Go to Settings > Apps > TouchX > Other Permissions and enable:\n• Show on Lock Screen\n• Display pop-up windows while running in background\n• Auto-launch\nWithout these, the lock screen will NOT appear.")
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.White)
                    ) {
                        Text("Got it", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HelpItem(title: String, description: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
        Text(description, color = Color.Black.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 18.sp)
    }
}

@Composable
fun DashboardCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isToggled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF111111)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, color = Color.Gray, fontSize = 14.sp)
            }
            Switch(
                checked = isToggled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF333333),
                    uncheckedThumbColor = Color.DarkGray,
                    uncheckedTrackColor = Color(0xFF1A1A1A),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun InstructionsDialog(onClose: () -> Unit, onNext: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).height(600.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFB0B0B0)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Instructions", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(
                            text = "\"Welcome to TouchX, the next generation of professional security for your Android device. Unlike traditional PINs or patterns that can be easily shoulder-surfed, TouchX uses your own visual memory to create a secure, invisible gateway. A Picture Password is a unique combination of three gestures—either single taps or straight-line swipes—drawn specifically over features of an image that only you recognize. By choosing a personal photograph from your gallery, you transform a simple image into a sophisticated biometric-style authentication key that is nearly impossible for others to guess or replicate.\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black,
                            lineHeight = 20.sp
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.White)
                    ) {
                        Text("Next", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UploadDialog(onClose: () -> Unit, onFinalNext: (Uri, GestureMode, Int) -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var gestureMode by remember { mutableStateOf(GestureMode.FREEHAND) }
    var gestureCount by remember { mutableIntStateOf(3) }
    
    val context = LocalContext.current
    
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> 
        uri?.let { 
            try {
                val internalUri = saveUriToInternalStorage(context, it)
                selectedUri = internalUri
                uploadError = null
            } catch (e: Exception) {
                uploadError = "Failed to process image: ${e.localizedMessage}"
            }
        }
    }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> 
        if (success) {
            tempPhotoUri?.let { uri ->
                try {
                    // 1. Save to app-specific internal storage for the lock screen
                    val internalUri = saveUriToInternalStorage(context, uri)
                    selectedUri = internalUri
                    uploadError = null
                    
                    // 2. Mirror to public device gallery (Pictures folder)
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bitmap = BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                saveToGallery(context, bitmap)
                            }
                        }
                    } catch (e: Exception) {
                        // Log gallery save error but don't fail the primary capture flow
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    uploadError = "Failed to capture image: ${e.localizedMessage}"
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun capturePhoto() {
        val permission = android.Manifest.permission.CAMERA
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val file = File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(permission)
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFB0B0B0)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Setup Image", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                        }
                    }
                    
                    if (uploadError != null) {
                        Text(uploadError!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Box(
                        modifier = Modifier.height(240.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Color.Transparent)
                            .drawDottedBorder(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedUri == null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.Black, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        modifier = Modifier.clickable { 
                                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFF222222)
                                    ) {
                                        Text("Upload", color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 14.sp)
                                    }
                                    Surface(
                                        modifier = Modifier.clickable { capturePhoto() },
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFF222222)
                                    ) {
                                        Text("Capture", color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 14.sp)
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    painter = rememberAsyncImagePainter(selectedUri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Column(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Button(onClick = { capturePhoto() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                                        Icon(Icons.Default.CameraAlt, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Re-capture")
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                                        Icon(Icons.Default.PhotoLibrary, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Re-upload")
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Gesture Type", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GestureOptionChip(
                                text = "Only Taps", 
                                selected = gestureMode == GestureMode.TAPS_ONLY,
                                onClick = { gestureMode = GestureMode.TAPS_ONLY }
                            )
                            GestureOptionChip(
                                text = "Freehand", 
                                selected = gestureMode == GestureMode.FREEHAND,
                                onClick = { gestureMode = GestureMode.FREEHAND }
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Text("Number of Gestures: $gestureCount", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { i ->
                                Surface(
                                    modifier = Modifier.size(40.dp).clickable { gestureCount = i },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (gestureCount == i) Color.Black else Color.White.copy(alpha = 0.5f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(i.toString(), color = if (gestureCount == i) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    
                    Button(
                        onClick = { selectedUri?.let { onFinalNext(it, gestureMode, gestureCount) } },
                        enabled = selectedUri != null,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.White)
                    ) {
                        Text("Start Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GestureOptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() }.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color.Black else Color.White.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(text, color = if (selected) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun saveToGallery(context: Context, bitmap: Bitmap) {
    val filename = "TouchX_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/TouchX")
    }
    
    val resolver = context.contentResolver
    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    
    imageUri?.let { uri ->
        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
    }
}

private fun saveUriToInternalStorage(context: Context, uri: Uri): Uri {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open stream")
        // Use unique filename to avoid Coil/System caching of the same URI
        val timestamp = System.currentTimeMillis()
        val file = File(context.filesDir, "locked_image_$timestamp.jpg")
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        throw e
    }
}

fun Modifier.drawDottedBorder() = this.drawWithContent {
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    drawContent()
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.5f),
        style = Stroke(width = 2.dp.toPx(), pathEffect = pathEffect),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
    )
}
