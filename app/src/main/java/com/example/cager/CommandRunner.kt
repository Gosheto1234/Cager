package com.example.cager

import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CommandRunner {
    /**
     * Execute your disable/restore sequence based on `disable`.
     * Skips commands that don't apply to this device.
     */
    suspend fun runCommands(context: Context, disable: Boolean) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)

        // Battery‑guard: if disable==true and battery < 15%, skip heavy modules entirely
        val guardEnabled = prefs.getBoolean(MainActivity.BATTERY_GUARD_KEY, true)
        if (disable && guardEnabled) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level < 15) {
                Log.w("CommandRunner", "Battery low ($level%), skipping disable")
                return@withContext
            }
        }

        // Load JSON modules
        val modules = ModuleLoader.load(context, prefs, disable).flatten()
        if (modules.isEmpty()) {
            Log.w("CommandRunner", "No modules selected! Check your prefs/assets/commands")
            return@withContext
        }

        for (cmd in modules) {
            // pre‑filter certain patterns:
            when {
                // skip pm commands for unknown packages
                cmd.startsWith("pm disable-user") || cmd.startsWith("pm enable") -> {
                    // extract the package name (last token)
                    val pkg = cmd.substringAfterLast(' ')
                    try {
                        context.packageManager.getPackageInfo(pkg, 0)
                    } catch (_: PackageManager.NameNotFoundException) {
                        Log.w("CommandRunner", "Skipping pm command for missing pkg: $pkg")
                        continue
                    }
                }

                // skip fstrim if binary is missing
                cmd.startsWith("fstrim") -> {
                    if (!File("/system/bin/fstrim").canExecute()) {
                        Log.w("CommandRunner", "Skipping fstrim: binary not found")
                        continue
                    }
                }

                // skip catch‑all appops
                cmd.startsWith("appops set --uid all") -> {
                    Log.w("CommandRunner", "Skipping unsupported appops command: $cmd")
                    continue
                }
            }

            // now actually run it
            try {
                val exit = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", cmd))
                    .waitFor()

                if (exit != 0) {
                    Log.w("CommandRunner", "Non‑zero exit ($exit) for: $cmd")
                } else {
                    Log.d("CommandRunner", "Succeeded: $cmd")
                }
            } catch (e: Exception) {
                Log.e("CommandRunner", "Failed to run: $cmd", e)
            }
        }
    }
}
