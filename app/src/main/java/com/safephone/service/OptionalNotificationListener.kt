package com.safephone.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional hook for future URL/title hints from notifications when the user enables it in system settings.
 */
class OptionalNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Reserved for future keyword / domain hints; disabled by default in prefs.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
