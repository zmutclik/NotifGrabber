package com.example.notifgrabber

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
        val templateInput = findViewById<EditText>(R.id.jsonTemplateInput)
        val saveBtn       = findViewById<Button>(R.id.saveButton)
        val resetBtn      = findViewById<Button>(R.id.resetTemplateButton)
        val testBtn       = findViewById<Button>(R.id.testWebhookButton)
        val permBtn       = findViewById<Button>(R.id.openSettingsButton)
        val statusText    = findViewById<TextView>(R.id.statusText)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        urlInput.setText(prefs.getString(KEY_WEBHOOK_URL, ""))
        templateInput.setText(prefs.getString(KEY_JSON_TEMPLATE, DEFAULT_TEMPLATE))

        updateStatus(statusText)

        saveBtn.setOnClickListener {
            val url      = urlInput.text.toString().trim()
            val template = templateInput.text.toString().trim()

            if (!isValidJsonTemplate(template)) {
                Toast.makeText(this, "Template JSON tidak valid!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString(KEY_WEBHOOK_URL, url)
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

            testBtn.isEnabled = false
            WebhookSender.send(url, payload) { success, code ->
                runOnUiThread {
                    testBtn.isEnabled = true
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
    }

    override fun onResume() {
        super.onResume()
        updateStatus(findViewById(R.id.statusText))
    }

    private fun updateStatus(statusText: TextView) {
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: ""
        val isEnabled = enabledListeners.contains(packageName)
        statusText.text = if (isEnabled) "Status: akses notifikasi AKTIF"
                          else           "Status: akses notifikasi BELUM aktif"
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

    companion object {
        const val PREFS_NAME        = "notifgrabber_prefs"
        const val KEY_WEBHOOK_URL   = "webhook_url"
        const val KEY_JSON_TEMPLATE = "json_template"

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
