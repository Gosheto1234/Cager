package com.example.cager

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RootNotifyService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Build the notification
        val note: Notification = NotificationCompat.Builder(this, MainActivity.ROOT_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Now set the notification's own flags—don't touch the 'flags' parameter!
        note.flags = note.flags or
                Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_NO_CLEAR

        // Start foreground
        startForeground(MainActivity.ROOT_NOTIFICATION_ID, note)

        // Return the desired restart behavior
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
