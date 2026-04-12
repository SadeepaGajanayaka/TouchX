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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
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
import com.companymade.touchx.viewmodel.ClockStyle

@Composable
fun MainSetupScreen(
    hasPassword: Boolean,
    hasOverlayPermission: Boolean,
    isBatteryOptimized: Boolean,
    onSetNewPassword: (Uri, GestureMode, Int) -> Unit,
    onLock: () -> Unit,
    onRemovePassword: () -> Unit,
    gestureColor: Int,
    onColorChange: (Int) -> Unit,
    clockStyle: ClockStyle,
    onClockStyleChange: (ClockStyle) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestAutoStart: () -> Unit
) {
    var showInstructions by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var hasSmsPermission by remember { 
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) 
    }
    var hasCallPermission by remember { 
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED) 
    }
    var hasNotificationAccess by remember {
        mutableStateOf(isNotificationServiceEnabled(context))
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasSmsPermission = isGranted }
    
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCallPermission = isGranted }

    // Sync on resume (in case they changed permissions in settings)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                hasCallPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
                hasNotificationAccess = isNotificationServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // FIXED HEADER
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
                        fontSize = 32.sp
                    ),
                    color = Color.White
                )
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
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
            
            Spacer(modifier = Modifier.height(24.dp))

            // SCROLLABLE CENTER
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    icon = Icons.Default.Textsms,
                    title = "SMS Notifications",
                    subtitle = "Show unread message count",
                    isToggled = hasSmsPermission,
                    onToggle = { 
                        if (hasSmsPermission) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } else {
                            smsPermissionLauncher.launch(android.Manifest.permission.READ_SMS)
                        }
                    }
                )
                DashboardCard(
                    icon = Icons.Default.Call,
                    title = "Call Notifications",
                    subtitle = "Show missed call count",
                    isToggled = hasCallPermission,
                    onToggle = { 
                        if (hasCallPermission) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } else {
                            callPermissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
                        }
                    }
                )
                DashboardCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "General Notifications",
                    subtitle = "Signs for WhatsApp, apps, etc.",
                    isToggled = hasNotificationAccess,
                    onToggle = { 
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }
                )
                DashboardCard(
                    icon = Icons.Default.RocketLaunch,
                    title = "Auto-Start",
                    subtitle = "Run on boot",
                    isToggled = false,
                    onToggle = onRequestAutoStart
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // GESTURE COLOR PICKER
                Text("Touch Color", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 24.dp)
                ) {
                    val palette = listOf(
                        0xFF00E5FF.toInt(), // Cyan (Default)
                        0xFFFFFFFF.toInt(), // White
                        0xFFFF1744.toInt(), // Red
                        0xFFF50057.toInt(), // Pink
                        0xFFD500F9.toInt(), // Deep Purple
                        0xFF651FFF.toInt(), // Indigo
                        0xFF2979FF.toInt(), // Blue
                        0xFF00B0FF.toInt(), // Light Blue
                        0xFF00E676.toInt(), // Green
                        0xFF76FF03.toInt(), // Light Green
                        0xFFC6FF00.toInt(), // Lime
                        0xFFFFEA00.toInt(), // Yellow
                        0xFFFFC400.toInt(), // Amber
                        0xFFFF9100.toInt(), // Orange
                        0xFFFF3D00.toInt(), // Deep Orange
                        0xFF3E2723.toInt(), // Brown
                        0xFF37474F.toInt(), // Blue Grey
                        0xFF212121.toInt(), // Blackish
                        0xFF8BC34A.toInt(), // Olive
                        0xFFFFB74D.toInt(), // Muted Orange
                        0xFF9575CD.toInt(), // Muted Purple
                        0xFF4DB6AC.toInt()  // Muted Teal
                    )
                    items(palette) { colorInt ->
                        val color = Color(colorInt)
                        val isSelected = colorInt.toInt() == gestureColor
                        
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                                .border(
                                    width = 3.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .clickable { onColorChange(colorInt.toInt()) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // CLOCK THEME SELECTOR
                Text("Clock Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(end = 24.dp)
                ) {
                    val styles = ClockStyle.values()
                    items(styles) { style ->
                        val isSelected = style == clockStyle
                        ClockStylePreviewCard(
                            style = style,
                            isSelected = isSelected,
                            onClick = { onClockStyleChange(style) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }

            // FIXED FOOTER
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showInstructions = true },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF222222),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (hasPassword) "Update Picture Password" else "Setup Picture Password", 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onLock,
                enabled = hasPassword,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasPassword) Color.White else Color(0xFF1A1A1A),
                    contentColor = if (hasPassword) Color.Black else Color.Gray
                )
            ) {
                Text("Lock Now", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp))
            }

            if (hasPassword) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRemovePassword,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A0000),
                        contentColor = Color.Red.copy(alpha = 0.9f)
                    ),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.2f))
                ) {
                    Text("Remove Password", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                }
                Spacer(modifier = Modifier.height(24.dp))
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
        modifier = Modifier.fillMaxWidth().height(80.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF111111)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Switch(
                checked = isToggled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(0.85f),
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 640.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF1A1A1A)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Mastering TouchX",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        InstructionStep("1. The Power of Visual Memory", "Forget PINs. TouchX uses 'spatial memory.' You'll pick a photo and touch specific landmarks that only you know. It's invisible, secure, and incredibly fast.")
                        InstructionStep("2. Choosing Your Landmarks", "Select an image with clear points (like eyes on a face or stars in a sky). You will set 3 or more gestures over these points.")
                        InstructionStep("3. Tap or Swipe?", "• Tap: A single touch on a spot.\n• Swipe: Connect two points with a line. Swipes are harder for others to track!")
                        InstructionStep("4. Manual Confirmation", "During setup, you MUST press 'Confirm' after each gesture. This ensures every touch is exactly where you want it.")
                        InstructionStep("5. Learn the Rhythm", "After setting your gestures, watch the 'Verification Playback.' This shows your code one last time. Memorize the order and location perfectly.")
                        InstructionStep("6. The Stealth Lock", "On your lock screen, no lines or dots will appear when you touch. Just tap your secret spots in order. The screen will stay clean to keep others guessing.")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Tip: Always use a photo you are familiar with. A picture of your pet or a favorite travel spot works best!",
                                modifier = Modifier.padding(12.dp),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("I'm Ready", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(desc, color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun UploadDialog(onClose: () -> Unit, onFinalNext: (Uri, GestureMode, Int) -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var gestureMode by remember { mutableStateOf(GestureMode.LINE) }
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
                                text = "Line & Taps", 
                                selected = gestureMode == GestureMode.LINE,
                                onClick = { gestureMode = GestureMode.LINE }
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

@Composable
fun ClockStylePreviewCard(
    style: ClockStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f)
    
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // MINI PHONE FRAME REPLICA
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFF2C3E50), Color(0xFF000000))
                        )
                    )
            ) {
                // CLOCK CONTENT BASED ON STYLE
                when (style) {
                    ClockStyle.CLASSIC -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("12:45", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("MON, 12 OCT", fontSize = 6.sp, color = Color.White.copy(alpha = 0.6f))
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.size(4.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
                                Box(Modifier.size(4.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
                            }
                        }
                    }
                    ClockStyle.MODERN -> {
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("12", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 16.sp)
                                Text("45", fontSize = 18.sp, fontWeight = FontWeight.ExtraLight, color = Color.White, lineHeight = 16.sp)
                            }
                            Box(Modifier.padding(horizontal = 4.dp).width(0.5.dp).height(24.dp).background(Color.White.copy(alpha = 0.3f)))
                            Column {
                                Text("MON", fontSize = 5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Box(Modifier.padding(top = 2.dp).size(4.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
                            }
                        }
                    }
                    ClockStyle.MINIMAL -> {
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 12.dp)
                        ) {
                            Text("12:45", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Monday", fontSize = 6.sp, color = Color.White.copy(alpha = 0.5f))
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.size(4.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
                        }
                    }
                    ClockStyle.ELEGANT -> {
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("12:45", fontSize = 20.sp, fontWeight = FontWeight.W100, color = Color.White, letterSpacing = 1.sp)
                            Text("OCTOBER 12", fontSize = 5.sp, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.size(4.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        Text(
            text = style.name.lowercase().replaceFirstChar { it.uppercase() },
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkgName)
}
