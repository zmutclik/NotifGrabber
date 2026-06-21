package com.example.notifgrabber

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // On some OEM skins (MIUI, ColorOS, etc.) the listener doesn't
            // always rebind automatically after boot. This nudges it.
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, NotifListenerService::class.java)
                )
            } catch (e: Exception) {
                // ignore, system will usually rebind on its own anyway
            }
        }
    }
}
