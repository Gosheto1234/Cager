package com.example.cager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.PendingIntent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class HousekeeperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "housekeeper_reminder"
        private const val NOTIF_ID = 42
    }

    override suspend fun doWork(): Result {
        // 1) Clear old notifications
        NotificationManagerCompat.from(applicationContext).cancelAll()

        // 2) Optionally clear your app's cache
        applicationContext.cacheDir.listFiles()
            ?.forEach { it.deleteRecursively() }

        // 3) Build the reboot/cache-clear reminder
        val intent = Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            // system icon—no custom drawable needed
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("System Maintenance")
            .setContentText("It’s been a while—consider rebooting or clearing caches.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // 4) Dispatch, guarding Android 13+ POST_NOTIFICATIONS
        val nm = NotificationManagerCompat.from(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    nm.notify(NOTIF_ID, builder.build())
                } catch (_: SecurityException) { /* ignore */ }
            }
            // else: permission not granted—skip sending
        } else {
            nm.notify(NOTIF_ID, builder.build())
        }

        return Result.success()
    }
}
