package com.example.cager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

class EmergencyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Launch on the IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            CommandRunner.runCommands(ctx, false)
        }
    }
}
