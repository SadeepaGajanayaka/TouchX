package com.companymade.touchx

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
    private var fromLockService = false
    private var wasLockedOnStart = false
    private var keyguardLock: KeyguardManager.KeyguardLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        setContent {
            val context = LocalContext.current
            viewModel = remember { PictureLockViewModel(context) }
            
            val isLocked by viewModel.isLocked.collectAsState()
            val hasOverlay by viewModel.hasOverlayPermission.collectAsState()
            val isBatteryOptimized by viewModel.isBatteryOptimized.collectAsState()
            val imageUri by viewModel.imageUri.collectAsState()
            val gestures by viewModel.gestures.collectAsState()

            // Initialize correct state immediately to avoid flipping
            var appState by remember(fromLockService) { 
                mutableStateOf(
                    when {
                        fromLockService && isLocked && imageUri != null && gestures.isNotEmpty() -> AppState.LOCKED
                        fromLockService -> AppState.SETUP
                        else -> AppState.SPLASH
                    }
                )
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
                        if (fromLockService && isLocked) {
                            dismissKeyguard()
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

            // When user successfully unlocks, dismiss keyguard and finish if from service
            LaunchedEffect(isLocked) {
                if (wasLockedOnStart && !isLocked) {
                    // Dismiss the native swipe-to-unlock keyguard
                    dismissKeyguard()
                    if (fromLockService) {
                        fromLockService = false
                        wasLockedOnStart = false
                        val act = context as? ComponentActivity
                        act?.let {
                            @Suppress("DEPRECATION")
                            it.overridePendingTransition(0, 0)
                            it.finish()
                            @Suppress("DEPRECATION")
                            it.overridePendingTransition(0, 0)
                        }
                    }
                }
            }

            BackHandler(enabled = isLocked) {
                // Consume back press when locked
            }
            
            TouchXApp(viewModel, fromLockService, appState)
        }
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
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
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
    initialState: AppState = AppState.SPLASH
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

    TouchXTheme {
        AnimatedContent(
            targetState = when {
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
                    onUnlock = { 
                        if (fromService) {
                            // Write directly to SharedPreferences WITHOUT updating ViewModel
                            // so Compose never recomposes to show the dashboard
                            // Use commit() for synchronous write to ensure state is saved before finish
                            context.getSharedPreferences("picture_lock", Context.MODE_PRIVATE)
                                .edit().putBoolean("is_locked", false).commit()
                            
                            (context as? ComponentActivity)?.let { act ->
                                @Suppress("DEPRECATION")
                                act.overridePendingTransition(0, 0)
                                act.finish()
                                @Suppress("DEPRECATION")
                                act.overridePendingTransition(0, 0)
                            }
                        } else {
                            viewModel.setLock(false)
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