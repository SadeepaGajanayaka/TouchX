package com.companymade.touchx

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateNotificationState()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateNotificationState()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateNotificationState()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isNotificationActive = false
    }

    companion object {
        var isNotificationActive = false
            private set

        fun triggerRebind(context: android.content.Context) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                requestRebind(android.content.ComponentName(context, NotificationService::class.java))
            }
        }
    }

    private fun updateNotificationState() {
        val activeNotifications = try {
            activeNotifications
        } catch (e: Exception) {
            null
        }
        
        // Filter out system notifications or our own app if desired
        isNotificationActive = activeNotifications?.any { sbn ->
            !sbn.isOngoing && sbn.packageName != packageName
        } ?: false
    }
}
