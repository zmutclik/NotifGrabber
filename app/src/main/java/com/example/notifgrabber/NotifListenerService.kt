package com.example.notifgrabber

import android.app.Notification
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

class NotifListenerService : NotificationListenerService() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        forward(sbn, "posted")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // Optional: also report when a notification disappears.
        // Comment out if you only care about new notifications.
        forward(sbn, "removed")
    }

    private fun forward(sbn: StatusBarNotification, event: String) {
        val webhookUrl = prefs.getString(MainActivity.KEY_WEBHOOK_URL, null)
        if (webhookUrl.isNullOrBlank()) return

        // Skip this app's own notifications, if it ever posts any.
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val payload = JSONObject().apply {
            put("event", event)
            put("package", sbn.packageName)
            put("app_name", appName)
            put("title", title)
            put("text", if (bigText.isNotBlank()) bigText else text)
            put("sub_text", subText)
            put("post_time", sbn.postTime)
            put("device_time", System.currentTimeMillis())
        }

        WebhookSender.send(webhookUrl, payload)
    }
}
