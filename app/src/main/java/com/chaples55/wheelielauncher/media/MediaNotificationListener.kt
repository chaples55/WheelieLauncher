package com.chaples55.wheelielauncher.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Required so [android.media.session.MediaSessionManager.getActiveSessions] can read
 * active media sessions for Now Playing artwork and controls.
 */
class MediaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}
