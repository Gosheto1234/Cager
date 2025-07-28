package com.example.cager

import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationHandlerService : NotificationListenerService() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // only suppress if we're actively caged
        if (!prefs.getBoolean("isCaged", false)) return

        val pkg = sbn.packageName
        val whitelist = prefs.getStringSet(MainActivity.WHITELIST_KEY, emptySet())!!

        // let any whitelisted package through
        if (whitelist.contains(pkg)) return

        when (prefs.getString(MainActivity.NOTIF_STYLE_KEY, "full")) {
            "silent", "icon" -> cancelNotification(sbn.key)
            // full → do nothing
        }
    }

    override fun onListenerConnected() {
        // when we connect, drop everything if we're caged
        if (prefs.getBoolean("isCaged", false)) {
            cancelAllNotifications()
        }
    }
}