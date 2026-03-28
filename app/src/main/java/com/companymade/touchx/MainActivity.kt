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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, null)
        }

        setContent {
            val context = LocalContext.current
            viewModel = remember { PictureLockViewModel(context) }
            
            val isLocked by viewModel.isLocked.collectAsState()
            val hasOverlay by viewModel.hasOverlayPermission.collectAsState()
            val isBatteryOptimized by viewModel.isBatteryOptimized.collectAsState()
            val imageUri by viewModel.imageUri.collectAsState()

            DisposableEffect(Lifecycle.Event.ON_RESUME) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.updatePermissionStates(context)
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

            BackHandler(enabled = isLocked) {
                // Consume back press when locked to prevent exiting the lock screen
            }
            
            TouchXApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }
}

@Composable
fun TouchXApp(viewModel: PictureLockViewModel) {
    var appState by remember { mutableStateOf(AppState.SPLASH) }
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

    TouchXTheme {
        AnimatedContent(
            targetState = when {
                appState == AppState.SPLASH -> AppState.SPLASH
                isLocked && imageUri != null && gestures.isNotEmpty() -> AppState.LOCKED
                isSettingPassword -> AppState.SET_PASSWORD
                else -> AppState.SETUP
            },
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
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
                    onUnlock = { viewModel.setLock(false) }
                )
                AppState.SET_PASSWORD -> SetPasswordScreen(
                    imageUri = tempSettingUri!!,
                    gestureMode = gestureMode,
                    targetCount = targetGestureCount,
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