package id.my.zmutclik.notifgrabber

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import org.json.JSONObject

class NotifListenerService : NotificationListenerService() {

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val CHANNEL_ID   = "notifgrabber_fg"
        private const val NOTIF_ID     = 1001
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        startForegroundService()
    }

    private fun startForegroundService() {
        // Buat notification channel (wajib Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notif Grabber Aktif",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service pemantau notifikasi"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        // Intent untuk buka MainActivity saat notifikasi diklik
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Notif Grabber")
            .setContentText("Memantau notifikasi berjalan...")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)          // tidak bisa di-swipe oleh user
            .setSilent(true)           // tidak bunyi / getar
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Otomatis coba reconnect jika listener terputus
        try {
            requestRebind(ComponentName(this, NotifListenerService::class.java))
        } catch (_: Exception) {}
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

        val extras  = sbn.notification.extras
        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()    ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()     ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) { sbn.packageName }

        // Ambil template & headers dari prefs
        val template = prefs.getString(MainActivity.KEY_JSON_TEMPLATE, MainActivity.DEFAULT_TEMPLATE)
            ?: MainActivity.DEFAULT_TEMPLATE
        val rawHeaders = prefs.getString(MainActivity.KEY_HEADERS, "") ?: ""
        val headers = MainActivity.parseHeaders(rawHeaders)

        val bodyText = if (bigText.isNotBlank()) bigText else text

        // Render placeholder → nilai nyata
        val rendered = template
            .replace("{{event}}",       event)
            .replace("{{package}}",     sbn.packageName)
            .replace("{{app_name}}",    appName)
            .replace("{{title}}",       escapeJson(title))
            .replace("{{text}}",        escapeJson(bodyText))
            .replace("{{sub_text}}",    escapeJson(subText))
            // Tangani kasus user membungkus timestamp dengan kutip ("{{post_time}}") maupun tidak
            .replace("\"{{post_time}}\"",   sbn.postTime.toString())
            .replace("\"{{device_time}}\"", System.currentTimeMillis().toString())
            .replace("{{post_time}}",   sbn.postTime.toString())
            .replace("{{device_time}}", System.currentTimeMillis().toString())

        // Pastikan hasil render tetap JSON valid sebelum dikirim
        val payload = try {
            JSONObject(rendered)
        } catch (e: Exception) {
            // Fallback ke payload minimal jika template rusak
            JSONObject().apply {
                put("event",   event)
                put("package", sbn.packageName)
                put("title",   title)
                put("text",    bodyText)
                put("error",   "template_invalid: ${e.message}")
            }
        }

        // Catat log sebelum kirim
        val logEntry = LogManager.LogEntry(
            time     = LogManager.nowString(),
            event    = event,
            appName  = appName,
            title    = title,
            success  = null,
            httpInfo = "mengirim…"
        )
        LogManager.add(this, logEntry)

        WebhookSender.send(webhookUrl, payload, headers) { success, info ->
            LogManager.updateLatest(this, success, info)
        }
    }

    /** Escape karakter khusus JSON agar nilai string tidak merusak struktur JSON. */
    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
