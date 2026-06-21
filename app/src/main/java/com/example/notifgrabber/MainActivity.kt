package com.example.notifgrabber

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput = findViewById<EditText>(R.id.webhookUrlInput)
        val saveBtn = findViewById<Button>(R.id.saveButton)
        val permBtn = findViewById<Button>(R.id.openSettingsButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        urlInput.setText(prefs.getString(KEY_WEBHOOK_URL, ""))

        updateStatus(statusText)

        saveBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            prefs.edit().putString(KEY_WEBHOOK_URL, url).apply()
            Toast.makeText(this, "Webhook URL tersimpan", Toast.LENGTH_SHORT).show()
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
        statusText.text = if (isEnabled) {
            "Status: akses notifikasi AKTIF"
        } else {
            "Status: akses notifikasi BELUM aktif"
        }
    }

    companion object {
        const val PREFS_NAME = "notifgrabber_prefs"
        const val KEY_WEBHOOK_URL = "webhook_url"
    }
}
