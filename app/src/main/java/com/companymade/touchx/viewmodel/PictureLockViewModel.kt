package com.companymade.touchx.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GestureType { TAP, LINE }
enum class GestureMode { TAPS_ONLY, LINE }
enum class ClockStyle { CLASSIC, MODERN, MINIMAL, ELEGANT }

data class PasswordGesture(
    val type: GestureType,
    val xStart: Float,
    val yStart: Float,
    val xEnd: Float,
    val yEnd: Float
)

class PictureLockViewModel(context: Context) : ViewModel() {
    private val sharedPref = context.getSharedPreferences("picture_lock", Context.MODE_PRIVATE)

    private val _imageUri = MutableStateFlow<Uri?>(
        sharedPref.getString("image_uri", null)?.let { Uri.parse(it) }
    )
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _gestureMode = MutableStateFlow(
        run {
            val saved = sharedPref.getString("gesture_mode", GestureMode.LINE.name)
            try { GestureMode.valueOf(saved!!) } catch (e: Exception) { GestureMode.LINE }
        }
    )
    val gestureMode: StateFlow<GestureMode> = _gestureMode.asStateFlow()

    private val _targetGestureCount = MutableStateFlow(
        sharedPref.getInt("target_gesture_count", 3)
    )
    val targetGestureCount: StateFlow<Int> = _targetGestureCount.asStateFlow()

    private val _gestureColor = MutableStateFlow(
        sharedPref.getInt("touch_color", 0xFF00E5FF.toInt())
    )
    val gestureColor: StateFlow<Int> = _gestureColor.asStateFlow()

    private val _gestures = MutableStateFlow<List<PasswordGesture>>(loadGestures())
    val gestures: StateFlow<List<PasswordGesture>> = _gestures.asStateFlow()

    private val _clockStyle = MutableStateFlow(
        run {
            val saved = sharedPref.getString("clock_style", ClockStyle.CLASSIC.name)
            try { ClockStyle.valueOf(saved!!) } catch (e: Exception) { ClockStyle.CLASSIC }
        }
    )
    val clockStyle: StateFlow<ClockStyle> = _clockStyle.asStateFlow()

    private val _isLocked = MutableStateFlow(sharedPref.getBoolean("is_locked", false))
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isSettingPassword = MutableStateFlow(false)
    val isSettingPassword: StateFlow<Boolean> = _isSettingPassword.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(true)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _isBatteryOptimized = MutableStateFlow(false)
    val isBatteryOptimized: StateFlow<Boolean> = _isBatteryOptimized.asStateFlow()

    private val _tempSettingUri = MutableStateFlow<Uri?>(null)
    val tempSettingUri: StateFlow<Uri?> = _tempSettingUri.asStateFlow()

    private fun loadGestures(): List<PasswordGesture> {
        val count = sharedPref.getInt("saved_gestures_count", 0)
        val list = mutableListOf<PasswordGesture>()
        for (i in 1..count) {
            val typeStr = sharedPref.getString("g${i}_type", null) ?: continue
            list.add(PasswordGesture(
                type = try { GestureType.valueOf(typeStr) } catch(e: Exception) { GestureType.TAP },
                xStart = sharedPref.getFloat("g${i}_xStart", -1f),
                yStart = sharedPref.getFloat("g${i}_yStart", -1f),
                xEnd = sharedPref.getFloat("g${i}_xEnd", -1f),
                yEnd = sharedPref.getFloat("g${i}_yEnd", -1f)
            ))
        }
        return list
    }

    fun updatePermissionStates(context: Context) {
        _isLocked.value = sharedPref.getBoolean("is_locked", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            _hasOverlayPermission.value = android.provider.Settings.canDrawOverlays(context)
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            _isBatteryOptimized.value = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    fun setLock(locked: Boolean) {
        _isLocked.value = locked
        sharedPref.edit().putBoolean("is_locked", locked).commit()
    }

    fun updateGestureSettings(mode: GestureMode, count: Int) {
        _gestureMode.value = mode
        _targetGestureCount.value = count
        sharedPref.edit()
            .putString("gesture_mode", mode.name)
            .putInt("target_gesture_count", count)
            .apply()
    }

    fun updateGestureColor(color: Int) {
        _gestureColor.value = color
        sharedPref.edit().putInt("touch_color", color).apply()
    }

    fun updateClockStyle(style: ClockStyle) {
        _clockStyle.value = style
        sharedPref.edit().putString("clock_style", style.name).apply()
    }

    fun startSettingPassword(uri: Uri) {
        _tempSettingUri.value = uri
        _isSettingPassword.value = true
    }

    fun cancelSettingPassword() {
        _isSettingPassword.value = false
    }

    fun savePassword(context: Context, uri: Uri, gestures: List<PasswordGesture>) {
        if (gestures.size < _targetGestureCount.value) return

        // CLEANUP PREVIOUS IMAGES BEFORE SAVING NEW ONE (Spare the current one)
        try {
            val currentFileName = uri.lastPathSegment
            context.filesDir.listFiles { _, name -> 
                name.startsWith("locked_image_") && name.endsWith(".jpg") && name != currentFileName
            }?.forEach { it.delete() }
        } catch (e: Exception) { e.printStackTrace() }

        _imageUri.value = uri
        _gestures.value = gestures
        _isLocked.value = true
        _isSettingPassword.value = false

        val editor = sharedPref.edit()
        editor.putString("image_uri", uri.toString())
        editor.putInt("saved_gestures_count", gestures.size)
        gestures.forEachIndexed { i, g ->
            val index = i + 1
            editor.putString("g${index}_type", g.type.name)
            editor.putFloat("g${index}_xStart", g.xStart)
            editor.putFloat("g${index}_yStart", g.yStart)
            editor.putFloat("g${index}_xEnd", g.xEnd)
            editor.putFloat("g${index}_yEnd", g.yEnd)
        }
        editor.putBoolean("is_locked", true)
        editor.apply()
    }

    fun clearPassword(context: Context) {
        _imageUri.value = null
        _gestures.value = emptyList()
        _isLocked.value = false
        
        sharedPref.edit().clear().apply()
        
        // CLEANUP ALL STORED IMAGES
        try {
            context.filesDir.listFiles { _, name -> name.startsWith("locked_image_") && name.endsWith(".jpg") }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
