package id.my.zmutclik.notifgrabber

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ── Minta izin notifikasi di Android 13+ ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        val urlInput      = findViewById<EditText>(R.id.webhookUrlInput)
        val headersInput  = findViewById<EditText>(R.id.headersInput)
        val templateInput = findViewById<EditText>(R.id.jsonTemplateInput)
        val saveBtn       = findViewById<Button>(R.id.saveButton)
        val resetBtn      = findViewById<Button>(R.id.resetTemplateButton)
        val testBtn       = findViewById<Button>(R.id.testWebhookButton)
        val permBtn       = findViewById<Button>(R.id.openSettingsButton)
        val batteryBtn    = findViewById<Button>(R.id.batteryOptButton)
        val filterBtn     = findViewById<Button>(R.id.appFilterButton)
        val filterStatus  = findViewById<TextView>(R.id.filterStatusText)
        val statusText    = findViewById<TextView>(R.id.statusText)
        val serviceStatus = findViewById<TextView>(R.id.serviceStatusText)
        val logContent    = findViewById<TextView>(R.id.logContentText)
        val logCount      = findViewById<TextView>(R.id.logCountText)
        val clearLogBtn   = findViewById<Button>(R.id.clearLogButton)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        urlInput.setText(prefs.getString(KEY_WEBHOOK_URL, ""))
        headersInput.setText(prefs.getString(KEY_HEADERS, ""))
        templateInput.setText(prefs.getString(KEY_JSON_TEMPLATE, DEFAULT_TEMPLATE))

        updateStatus(statusText)

        saveBtn.setOnClickListener {
            val url      = urlInput.text.toString().trim()
            val headers  = headersInput.text.toString().trim()
            val template = templateInput.text.toString().trim()

            if (!isValidJsonTemplate(template)) {
                Toast.makeText(this, "Template JSON tidak valid!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!isValidHeaders(headers)) {
                Toast.makeText(this, "Format header tidak valid! Gunakan: Key: Value", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString(KEY_WEBHOOK_URL, url)
                .putString(KEY_HEADERS, headers)
                .putString(KEY_JSON_TEMPLATE, template)
                .apply()
            Toast.makeText(this, "Pengaturan tersimpan", Toast.LENGTH_SHORT).show()
        }

        resetBtn.setOnClickListener {
            templateInput.setText(DEFAULT_TEMPLATE)
            Toast.makeText(this, "Template direset ke default", Toast.LENGTH_SHORT).show()
        }

        testBtn.setOnClickListener {
            val url      = urlInput.text.toString().trim()
            val template = templateInput.text.toString().trim()

            if (url.isBlank()) {
                Toast.makeText(this, "Isi URL webhook terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isValidJsonTemplate(template)) {
                Toast.makeText(this, "Template JSON tidak valid!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val now = System.currentTimeMillis()
            val testTitle = "Test Notifikasi"
            val testText  = "Ini adalah pesan uji coba dari Notif Grabber"

            val rendered = template
                .replace("{{event}}",       "test")
                .replace("{{package}}",     packageName)
                .replace("{{app_name}}",    "Notif Grabber")
                .replace("{{title}}",       testTitle)
                .replace("{{text}}",        testText)
                .replace("{{sub_text}}",    "")
                .replace("\"{{post_time}}\"",   now.toString())
                .replace("\"{{device_time}}\"", now.toString())
                .replace("{{post_time}}",   now.toString())
                .replace("{{device_time}}", now.toString())

            val payload = try {
                JSONObject(rendered)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal render template: ${e.message}", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val headers = headersInput.text.toString().trim()
            val parsedHeaders = MainActivity.parseHeaders(headers)

            testBtn.isEnabled = false

            // ── Tampilkan notifikasi lokal sebagai test ──
            showTestNotification(testTitle, testText)

            // Tambah log test
            LogManager.add(this, LogManager.LogEntry(
                time = LogManager.nowString(), event = "test",
                appName = "Notif Grabber", title = testTitle,
                success = null, httpInfo = "mengirim…"
            ))
            refreshLog(logContent, logCount)

            WebhookSender.send(url, payload, parsedHeaders) { success, code ->
                LogManager.updateLatest(this, success, code)
                runOnUiThread {
                    testBtn.isEnabled = true
                    refreshLog(logContent, logCount)
                    if (success) {
                        Toast.makeText(this, "✓ Test berhasil dikirim (HTTP $code)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "✗ Gagal: $code", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        permBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // ── Battery Optimization ──
        batteryBtn.setOnClickListener {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Battery optimization sudah dimatikan", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        // ── Filter Aplikasi ──
        filterBtn.setOnClickListener {
            startActivity(Intent(this, AppFilterActivity::class.java))
        }
        updateFilterStatus(filterStatus)

        // Clear log
        clearLogBtn.setOnClickListener {
            LogManager.clear(this)
            refreshLog(logContent, logCount)
            Toast.makeText(this, "Log dihapus", Toast.LENGTH_SHORT).show()
        }

        // Tampilkan log awal
        refreshLog(logContent, logCount)
    }

    override fun onResume() {
        super.onResume()
        updateStatus(findViewById(R.id.statusText))
        updateServiceStatus(findViewById(R.id.serviceStatusText))
        updateFilterStatus(findViewById(R.id.filterStatusText))
        refreshLog(findViewById(R.id.logContentText), findViewById(R.id.logCountText))
    }

    private fun updateStatus(statusText: TextView) {
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: ""
        val isEnabled = enabledListeners.contains(packageName)
        statusText.text = if (isEnabled) "✅ Status: akses notifikasi AKTIF"
                          else           "❌ Status: akses notifikasi BELUM aktif"
    }

    private fun updateServiceStatus(statusText: TextView) {
        val sb = StringBuilder()

        // Cek notification listener service
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: ""
        val listenerActive = enabledListeners.contains(packageName)
        sb.appendLine(if (listenerActive) "✅ Notification Listener: AKTIF"
                      else                "❌ Notification Listener: NONAKTIF")

        // Cek foreground service running
        val serviceRunning = isNotifServiceRunning()
        sb.appendLine(if (serviceRunning) "✅ Foreground Service: BERJALAN"
                      else                "❌ Foreground Service: TIDAK berjalan")

        // Cek battery optimization
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        sb.appendLine(if (ignoringBattery) "✅ Battery Optimization: DIMATIKAN (bagus!)"
                      else                 "⚠️ Battery Optimization: AKTIF (bisa blokir service)")

        statusText.text = sb.toString().trimEnd()
    }

    private fun isNotifServiceRunning(): Boolean {
        // Use the static flag from NotifListenerService instead of
        // the deprecated getRunningServices() which doesn't work on newer Android versions
        return NotifListenerService.isRunning
    }

    private fun updateFilterStatus(statusText: TextView) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val mode = prefs.getString(KEY_FILTER_MODE, "all") ?: "all"
        val apps = prefs.getStringSet(KEY_FILTER_APPS, emptySet()) ?: emptySet()
        statusText.text = when (mode) {
            "whitelist" -> "Mode: Whitelist (${apps.size} aplikasi)"
            "blacklist" -> "Mode: Blacklist (${apps.size} aplikasi)"
            else        -> "Mode: Semua Aplikasi"
        }
    }

    /** Render semua log ke TextView. */
    private fun refreshLog(contentView: TextView, countView: TextView) {
        val entries = LogManager.getAll(this)
        countView.text = "${entries.size} entri"
        if (entries.isEmpty()) {
            contentView.text = "(belum ada log)"
            return
        }
        val sb = StringBuilder()
        entries.forEach { e ->
            val icon = when {
                e.success == null -> "⏳"
                e.success == true -> "✅"
                else              -> "❌"
            }
            val ev = when (e.event) {
                "posted"  -> "📥"
                "removed" -> "📤"
                else      -> "🧪"
            }
            sb.appendLine("${e.time}  $icon $ev ${e.event.uppercase()}")
            sb.appendLine("   📱 ${e.appName}  📝 ${e.title}")
            sb.appendLine("   → ${e.httpInfo}")
            sb.appendLine()
        }
        contentView.text = sb.toString().trimEnd()
    }

    /**
     * Validasi template dengan mengganti semua placeholder angka ke nilai dummy
     * lalu parse sebagai JSONObject.
     */
    private fun isValidJsonTemplate(template: String): Boolean = try {
        val dummy = template
            .replace("{{post_time}}", "0")
            .replace("{{device_time}}", "0")
        JSONObject(dummy)
        true
    } catch (_: Exception) { false }

    /** Validasi semua baris header berformat "Key: Value" (baris kosong dilewati). */
    private fun isValidHeaders(raw: String): Boolean {
        if (raw.isBlank()) return true
        return raw.lines().filter { it.isNotBlank() }.all { line ->
            val idx = line.indexOf(':')
            idx > 0 && line.substring(0, idx).isNotBlank()
        }
    }

    /** Tampilkan notifikasi lokal sebagai test — user bisa melihat & listener juga bisa menangkap. */
    private fun showTestNotification(title: String, text: String) {
        val channelId = "notifgrabber_test"
        val notifId   = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Test Notifikasi",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel untuk notifikasi test dari Notif Grabber"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(notifId, notification)
    }



    companion object {
        const val PREFS_NAME        = "notifgrabber_prefs"
        const val KEY_WEBHOOK_URL   = "webhook_url"
        const val KEY_HEADERS       = "webhook_headers"
        const val KEY_JSON_TEMPLATE = "json_template"
        const val KEY_FILTER_MODE   = "filter_mode"      // "all", "whitelist", "blacklist"
        const val KEY_FILTER_APPS   = "filter_apps"      // Set<String> package names

        /** Parse teks header menjadi Map<String, String>. */
        fun parseHeaders(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()
            return raw.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key   = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim()
                        if (key.isNotEmpty()) key to value else null
                    } else null
                }.toMap()
        }

        val DEFAULT_TEMPLATE = """
            {
              "event":       "{{event}}",
              "package":     "{{package}}",
              "app_name":    "{{app_name}}",
              "title":       "{{title}}",
              "text":        "{{text}}",
              "sub_text":    "{{sub_text}}",
              "post_time":   {{post_time}},
              "device_time": {{device_time}}
            }
        """.trimIndent()

        /**
         * Cek apakah package harus diteruskan berdasarkan filter.
         * @return true jika notifikasi boleh diteruskan.
         */
        fun shouldForward(prefs: SharedPreferences, pkgName: String): Boolean {
            val mode = prefs.getString(KEY_FILTER_MODE, "all") ?: "all"
            if (mode == "all") return true
            val apps = prefs.getStringSet(KEY_FILTER_APPS, emptySet()) ?: emptySet()
            return when (mode) {
                "whitelist" -> apps.contains(pkgName)   // hanya terpilih yang lolos
                "blacklist" -> !apps.contains(pkgName)  // terpilih di-skip
                else        -> true
            }
        }
    }
}
