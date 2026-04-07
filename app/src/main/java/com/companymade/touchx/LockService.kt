package com.companymade.touchx

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LockService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sharedPref = context.getSharedPreferences("picture_lock", Context.MODE_PRIVATE)
            val hasImage = sharedPref.getString("image_uri", null) != null
            
            if (!hasImage) return

            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    sharedPref.edit().putBoolean("is_locked", true).apply()
                }
                Intent.ACTION_SCREEN_ON -> {
                    val isLocked = sharedPref.getBoolean("is_locked", false)
                    if (isLocked) {
                        val lockIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                                     Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                                     Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                     Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            putExtra("FROM_LOCK_SERVICE", true)
                        }
                        try {
                            context.startActivity(lockIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        refreshNotification()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(receiver, filter)
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "lock_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TouchX Protection",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        refreshNotification()
    }

    private fun refreshNotification() {
        val channelId = "lock_service_channel"
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TouchX Secure")
            .setContentText("Lock screen protection is active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
