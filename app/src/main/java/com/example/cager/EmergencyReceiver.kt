package com.example.cager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class EmergencyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            CommandRunner.runCommands(ctx, false)

            ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.IS_CAGED_KEY, false)
                .apply()

            ctx.stopService(Intent(ctx, PowerCageService::class.java))

            val req = PeriodicWorkRequest.Builder(
                HousekeeperWorker::class.java,
                12L,
                TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "housekeeper",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}