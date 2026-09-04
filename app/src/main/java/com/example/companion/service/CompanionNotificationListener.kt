package com.example.companion.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

class CompanionNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.isOngoing) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val pack = sbn.packageName

        if (title.isEmpty() && text.isEmpty()) return

        val json = JSONObject().apply {
            put("type", "notification")
            put("package", pack)
            put("title", title)
            put("body", text)
        }

        val intent = Intent(this, DeviceCompanionService::class.java).apply {
            action = DeviceCompanionService.ACTION_FORWARD_NOTIFICATION
            putExtra("data", json.toString())
        }
        startService(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
