package com.companymade.touchx

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.companymade.touchx.ui.screens.*
import com.companymade.touchx.ui.theme.TouchXTheme
import com.companymade.touchx.viewmodel.PictureLockViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: PictureLockViewModel
    private var fromLockService by mutableStateOf(false)
    private var wasLockedOnStart by mutableStateOf(false)
    private var isUnlocking = false
    private var keyguardLock: KeyguardManager.KeyguardLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = PictureLockViewModel(applicationContext)
        fromLockService = intent?.getBooleanExtra("FROM_LOCK_SERVICE", false) == true
        
        // Suppress app opening animation when triggered from service
        if (fromLockService) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        
        applyLockScreenFlags()

        // Ensure the window fits the screen perfectly without flicker
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                       WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        
        // Optimize window for lock screen display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Dismiss the native swipe-to-unlock immediately
        dismissKeyguard()
        
        // Hide system bars completely and make them transparent
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val context = LocalContext.current
            // Use the activity-level viewModel
            
            val isLocked by viewModel.isLocked.collectAsState()
            val hasOverlay by viewModel.hasOverlayPermission.collectAsState()
            val isBatteryOptimized by viewModel.isBatteryOptimized.collectAsState()
            val imageUri by viewModel.imageUri.collectAsState()
            val gestures by viewModel.gestures.collectAsState()

            // Auto-hide system UI whenever we are in LOCKED state
            LaunchedEffect(isLocked) {
                if (isLocked) {
                    hideSystemUI()
                }
            }

            // Track actual current state based on service vs app usage
            var appState by remember { mutableStateOf(AppState.SPLASH) }
            
            // Re-sync appState when fromLockService or isLocked changes
            LaunchedEffect(fromLockService, isLocked) {
                if (fromLockService && isLocked) {
                    appState = AppState.LOCKED
                } else if (appState == AppState.SPLASH && !fromLockService) {
                    // Normal start
                }
            }

            // Track that user was locked when activity started
            LaunchedEffect(fromLockService, isLocked) {
                if (fromLockService) {
                    wasLockedOnStart = isLocked
                }
            }

            DisposableEffect(Lifecycle.Event.ON_RESUME) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.updatePermissionStates(context)
                        if ((fromLockService || viewModel.isLocked.value)) {
                            dismissKeyguard()
                            hideSystemUI()
                        }
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            
            LaunchedEffect(imageUri) {
                if (imageUri != null) {
                    val serviceIntent = Intent(context, LockService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    context.stopService(Intent(context, LockService::class.java))
                }
            }

            // The final unlock logic is now handled inside TouchXApp's onUnlock callback
            // for more precise control over transition vs finish.

            BackHandler(enabled = isLocked) {
                // Consume back press when locked
            }
            
            TouchXApp(
                viewModel = viewModel,
                fromService = fromLockService,
                initialState = appState,
                onDismissKeyguard = { dismissKeyguard() },
                onUnlockStarted = { isUnlocking = true }
            )
            
            // Dynamically manage Recent Apps visibility based on service usage
            LaunchedEffect(fromLockService) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val currentTaskId = (context as Activity).taskId
                    am.appTasks.forEach { task ->
                        val info = task.taskInfo
                        @Suppress("DEPRECATION")
                        val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.taskId else info.id
                        if (id == currentTaskId) {
                            task.setExcludeFromRecents(fromLockService)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (fromLockService) {
            applyLockScreenFlags()
        }
        // Recover from system notification unbind errors
        NotificationService.triggerRebind(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        fromLockService = intent.getBooleanExtra("FROM_LOCK_SERVICE", false)
        if (fromLockService) {
            wasLockedOnStart = true
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            
            applyLockScreenFlags()
            dismissKeyguard()
        }
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }
    }

    private fun dismissKeyguard() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, null)
        }
        
        // Legacy fallback for better compatibility on various manufacturers
        @Suppress("DEPRECATION")
        if (keyguardLock == null) {
            keyguardLock = km.newKeyguardLock("TouchX:Lock")
        }
        keyguardLock?.disableKeyguard()
        
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        // ABSOLUTE BLOCK: If the device tries to force split screen, snap back to full screen
        if (isInMultiWindowMode && (fromLockService || viewModel.isLocked.value)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("FROM_LOCK_SERVICE", true)
            }
            startActivity(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        // Aggressive snap-back: If user tries to swipe up for Recent Apps or Home,
        // we immediately bring the activity back to focus if the screen is still locked.
        // We SKIP this if we are currently in the process of a valid UNLOCK.
        if (!isUnlocking && (fromLockService || viewModel.isLocked.value)) {
            if (viewModel.isLocked.value) {
                val restartIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("FROM_LOCK_SERVICE", true)
                }
                startActivity(restartIntent)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Specifically for Home button / Home gesture
        if (fromLockService || viewModel.isLocked.value) {
            val isStillLocked = viewModel.isLocked.value
            if (isStillLocked) {
                val restartIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("FROM_LOCK_SERVICE", true)
                }
                startActivity(restartIntent)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && (fromLockService || viewModel.isLocked.value)) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Removed reenableKeyguard() to prevent system lock from reappearing after successful unlock.
        // The system will naturally restore the keyguard on the next screen-off event.
    }
}

@Composable
fun TouchXApp(
    viewModel: PictureLockViewModel, 
    fromService: Boolean = false,
    initialState: AppState = AppState.SPLASH,
    onDismissKeyguard: () -> Unit,
    onUnlockStarted: () -> Unit
) {
    var appState by remember(initialState) { mutableStateOf(initialState) }
    val context = LocalContext.current
    val imageUri by viewModel.imageUri.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val isSettingPassword by viewModel.isSettingPassword.collectAsState()
    val hasOverlay by viewModel.hasOverlayPermission.collectAsState()
    val isBatteryOptimized by viewModel.isBatteryOptimized.collectAsState()
    val gestures by viewModel.gestures.collectAsState()
    val gestureMode by viewModel.gestureMode.collectAsState()
    val targetGestureCount by viewModel.targetGestureCount.collectAsState()
    val tempSettingUri by viewModel.tempSettingUri.collectAsState()
    val gestureColor by viewModel.gestureColor.collectAsState()
    val clockStyle by viewModel.clockStyle.collectAsState()

    TouchXTheme {
        AnimatedContent(
            targetState = when {
                // If started from service, we EXCLUSIVELY stay in LOCKED mode.
                // We never allow it to transition to SETUP in this instance.
                fromService -> AppState.LOCKED
                
                appState == AppState.SPLASH -> AppState.SPLASH
                isLocked && imageUri != null && gestures.isNotEmpty() -> AppState.LOCKED
                isSettingPassword -> AppState.SET_PASSWORD
                else -> AppState.SETUP
            },
            transitionSpec = {
                val duration = if (fromService) 0 else 500
                fadeIn(animationSpec = tween(duration)) togetherWith fadeOut(animationSpec = tween(duration))
            },
            label = "AppScreenTransition"
        ) { state ->
            when (state) {
                AppState.SPLASH -> SplashScreen(
                    onFinish = { appState = AppState.SETUP }
                )
                AppState.LOCKED -> PictureLockScreen(
                    imageUri = imageUri!!,
                    gestures = gestures,
                    gestureColor = gestureColor,
                    clockStyle = clockStyle,
                    onUnlock = { 
                        onUnlockStarted()
                        // Synchronously save the unlock state to disk immediately
                        context.getSharedPreferences("picture_lock", Context.MODE_PRIVATE)
                            .edit().putBoolean("is_locked", false).commit()
                            
                        // Also update ViewModel state so other components (like onPause) know we are unlocked
                        viewModel.setLock(false)
                            
                        if (fromService) {
                            // If we came from the lock service, just finish the activity after dismissing keyguard
                            onDismissKeyguard()
                            (context as? ComponentActivity)?.let { act ->
                                @Suppress("DEPRECATION")
                                act.overridePendingTransition(0, 0)
                                act.finish()
                                @Suppress("DEPRECATION")
                                act.overridePendingTransition(0, 0)
                            }
                        }
                    }
                )
                AppState.SET_PASSWORD -> SetPasswordScreen(
                    imageUri = tempSettingUri!!,
                    gestureMode = gestureMode,
                    targetCount = targetGestureCount,
                    gestureColor = gestureColor,
                    onPasswordSet = { uri, gest -> viewModel.savePassword(context, uri, gest) },
                    onCancel = { viewModel.cancelSettingPassword() }
                )
                AppState.SETUP -> MainSetupScreen(
                    hasPassword = imageUri != null && gestures.isNotEmpty(),
                    hasOverlayPermission = hasOverlay,
                    isBatteryOptimized = isBatteryOptimized,
                    onSetNewPassword = { uri, mode, count -> 
                        viewModel.updateGestureSettings(mode, count)
                        viewModel.startSettingPassword(uri) 
                    },
                    onLock = { viewModel.setLock(true) },
                    onRemovePassword = { viewModel.clearPassword(context) },
                    gestureColor = gestureColor,
                    onColorChange = { viewModel.updateGestureColor(it) },
                    clockStyle = clockStyle,
                    onClockStyleChange = { viewModel.updateClockStyle(it) },
                    onRequestOverlay = { 
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    },
                    onRequestBattery = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    onRequestAutoStart = {
                        val manufacturer = Build.MANUFACTURER.lowercase()
                        val intents = mutableListOf<Intent>()
                        when {
                            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                            }
                            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                            }
                            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                            }
                            manufacturer.contains("vivo") -> {
                                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                            }
                        }
                        val launched = intents.any { intent ->
                            try {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                true
                            } catch (_: Exception) { false }
                        }
                        if (!launched) {
                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            fallback.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(fallback)
                        }
                    }
                )
            }
        }
    }
}

enum class AppState { SPLASH, SETUP, SET_PASSWORD, LOCKED }