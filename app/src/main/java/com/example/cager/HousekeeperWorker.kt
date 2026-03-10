package com.example.cager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result

class HousekeeperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "housekeeper_reminder"
        private const val NOTIF_ID = 42
    }

    override suspend fun doWork(): Result {
        createNotificationChannelIfNeeded()

        NotificationManagerCompat.from(applicationContext).cancelAll()

        applicationContext.cacheDir.listFiles()
            ?.forEach { it.deleteRecursively() }

        val intent = Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("System Maintenance")
            .setContentText("It’s been a while—consider rebooting or clearing caches.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val nm = NotificationManagerCompat.from(applicationContext)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    nm.notify(NOTIF_ID, builder.build())
                } catch (_: SecurityException) {
                }
            }
        } else {
            nm.notify(NOTIF_ID, builder.build())
        }

        return Result.success()
    }

    private fun createNotificationChannelIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Housekeeper Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            applicationContext
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}