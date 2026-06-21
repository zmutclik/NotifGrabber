package id.my.zmutclik.notifgrabber

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput      = findViewById<EditText>(R.id.webhookUrlInput)
        val headersInput  = findViewById<EditText>(R.id.headersInput)
        val templateInput = findViewById<EditText>(R.id.jsonTemplateInput)
        val saveBtn       = findViewById<Button>(R.id.saveButton)
        val resetBtn      = findViewById<Button>(R.id.resetTemplateButton)
        val testBtn       = findViewById<Button>(R.id.testWebhookButton)
        val permBtn       = findViewById<Button>(R.id.openSettingsButton)
        val statusText    = findViewById<TextView>(R.id.statusText)
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
            val rendered = template
                .replace("{{event}}",       "test")
                .replace("{{package}}",     packageName)
                .replace("{{app_name}}",    "Notif Grabber")
                .replace("{{title}}",       "Test Notifikasi")
                .replace("{{text}}",        "Ini adalah pesan uji coba dari Notif Grabber")
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
            // Tambah log test
            LogManager.add(this, LogManager.LogEntry(
                time = LogManager.nowString(), event = "test",
                appName = "Notif Grabber", title = "Test Notifikasi",
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
        refreshLog(findViewById(R.id.logContentText), findViewById(R.id.logCountText))
    }

    private fun updateStatus(statusText: TextView) {
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: ""
        val isEnabled = enabledListeners.contains(packageName)
        statusText.text = if (isEnabled) "Status: akses notifikasi AKTIF"
                          else           "Status: akses notifikasi BELUM aktif"
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



    companion object {
        const val PREFS_NAME        = "notifgrabber_prefs"
        const val KEY_WEBHOOK_URL   = "webhook_url"
        const val KEY_HEADERS       = "webhook_headers"
        const val KEY_JSON_TEMPLATE = "json_template"

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
    }
}
