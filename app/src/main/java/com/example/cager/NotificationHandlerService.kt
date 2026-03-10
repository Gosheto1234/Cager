package com.example.cager

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationHandlerService : NotificationListenerService() {
    private lateinit var prefs: android.content.SharedPreferences

    private val forcedSuppressPackages = setOf(
        "com.google.android.gms",
        "com.android.vending"
    )

    private val forcedSuppressText = listOf(
        "enable google play services",
        "update google play services",
        "google play services"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.getBoolean(MainActivity.IS_CAGED_KEY, false)) return

        val whitelist = prefs.getStringSet(MainActivity.WHITELIST_KEY, emptySet())!!
        val pkg = sbn.packageName
        val uidStr = sbn.uid.toString()

        // Let explicitly whitelisted packages or UIDs through
        if (whitelist.contains(pkg) || whitelist.contains(uidStr)) return

        if (shouldForceSuppress(sbn)) {
            cancelNotification(sbn.key)
            return
        }

        when (prefs.getString(MainActivity.NOTIF_STYLE_KEY, "full")) {
            "silent", "icon" -> cancelNotification(sbn.key)
            "full" -> {
                // keep normal notifications unless force-suppressed above
            }
        }
    }

    override fun onListenerConnected() {
        if (!prefs.getBoolean(MainActivity.IS_CAGED_KEY, false)) return

        activeNotifications?.forEach { sbn ->
            val whitelist = prefs.getStringSet(MainActivity.WHITELIST_KEY, emptySet())!!
            val pkg = sbn.packageName
            val uidStr = sbn.uid.toString()

            if (whitelist.contains(pkg) || whitelist.contains(uidStr)) return@forEach

            if (shouldForceSuppress(sbn)) {
                cancelNotification(sbn.key)
                return@forEach
            }

            when (prefs.getString(MainActivity.NOTIF_STYLE_KEY, "full")) {
                "silent", "icon" -> cancelNotification(sbn.key)
            }
        }
    }

    private fun shouldForceSuppress(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in forcedSuppressPackages) return true

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.lowercase()
            .orEmpty()

        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?.lowercase()
            .orEmpty()

        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?.lowercase()
            .orEmpty()

        return forcedSuppressText.any { key ->
            title.contains(key) || text.contains(key) || bigText.contains(key)
        }
    }
}