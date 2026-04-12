package com.companymade.touchx

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    companion object {
        var isNotificationActive = false
            private set
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateNotificationState()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateNotificationState()
    }

    override fun onListenerConnected() {
        updateNotificationState()
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
