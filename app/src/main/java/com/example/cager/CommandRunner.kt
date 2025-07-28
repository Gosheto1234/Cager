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
    suspend fun runCommands(context: Context, disable: Boolean) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(
                MainActivity.PREFS,
                Context.MODE_PRIVATE
            )

            // Battery‑guard: if disable==true and battery < 15%, skip heavy modules entirely
            val guardEnabled = prefs.getBoolean(
                MainActivity.BATTERY_GUARD_KEY,
                true
            )
            if (disable && guardEnabled) {
                val bm = context.getSystemService(
                    Context.BATTERY_SERVICE
                ) as BatteryManager
                val level = bm.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )
                if (level < 15) {
                    Log.w(
                        "CommandRunner",
                        "Battery low ($level%), skipping disable"
                    )
                    return@withContext
                }
            }

            // Packages whose notifications should be suppressed/restored
            val suppressPkgs = listOf(
                "com.google.android.projection.gearhead",   // Android Auto
                "com.samsung.android.customization",        // Customization Service
                "com.samsung.android.visit",                // Samsung Visit
                "com.samsung.android.app.quickshare"        // Quick Share Service
            )

            if (disable) {
                // Block notifications before disabling modules
                suppressPkgs.forEach { pkg ->
                    try {
                        Runtime.getRuntime()
                            .exec(arrayOf("su", "-c", "cmd notification block $pkg"))
                            .waitFor()
                    } catch (e: Exception) {
                        Log.w(
                            "CommandRunner",
                            "Failed to block notifications for $pkg",
                            e
                        )
                    }
                }
                // Completely disable Quick Share to prevent its UI
                try {
                    Runtime.getRuntime()
                        .exec(
                            arrayOf(
                                "su",
                                "-c",
                                "pm disable-user --user 0 com.samsung.android.app.quickshare"
                            )
                        )
                        .waitFor()
                } catch (e: Exception) {
                    Log.w("CommandRunner", "Failed to disable Quick Share", e)
                }
            }

            // Load JSON modules
            val modules = ModuleLoader.load(context, prefs, disable).flatten()
            if (modules.isEmpty()) {
                Log.w(
                    "CommandRunner",
                    "No modules selected! Check your prefs/assets/commands"
                )
                return@withContext
            }

            modules.forEach { cmd ->
                // pre‑filter certain patterns:
                when {
                    cmd.startsWith("pm disable-user") || cmd.startsWith("pm enable") -> {
                        val pkg = cmd.substringAfterLast(' ')
                        try {
                            context.packageManager.getPackageInfo(pkg, 0)
                        } catch (_: PackageManager.NameNotFoundException) {
                            Log.w(
                                "CommandRunner",
                                "Skipping pm command for missing pkg: $pkg"
                            )
                            return@forEach
                        }
                    }
                    cmd.startsWith("fstrim") -> {
                        if (!File("/system/bin/fstrim").canExecute()) {
                            Log.w(
                                "CommandRunner",
                                "Skipping fstrim: binary not found"
                            )
                            return@forEach
                        }
                    }
                    cmd.startsWith("appops set --uid all") -> {
                        Log.w(
                            "CommandRunner",
                            "Skipping unsupported appops command: $cmd"
                        )
                        return@forEach
                    }
                }

                // run the command as root
                try {
                    val exit = Runtime.getRuntime()
                        .exec(arrayOf("su", "-c", cmd))
                        .waitFor()
                    if (exit != 0) {
                        Log.w(
                            "CommandRunner",
                            "Non‑zero exit ($exit) for: $cmd"
                        )
                    } else {
                        Log.d(
                            "CommandRunner",
                            "Succeeded: $cmd"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(
                        "CommandRunner",
                        "Failed to run: $cmd",
                        e
                    )
                }
            }

            if (!disable) {
                // Unblock notifications after restoring modules
                suppressPkgs.forEach { pkg ->
                    try {
                        Runtime.getRuntime()
                            .exec(
                                arrayOf(
                                    "su",
                                    "-c",
                                    "cmd notification unblock $pkg"
                                )
                            )
                            .waitFor()
                    } catch (e: Exception) {
                        Log.w(
                            "CommandRunner",
                            "Failed to unblock notifications for $pkg",
                            e
                        )
                    }
                }
                // Re-enable Quick Share UI
                try {
                    Runtime.getRuntime()
                        .exec(
                            arrayOf(
                                "su",
                                "-c",
                                "pm enable com.samsung.android.app.quickshare"
                            )
                        )
                        .waitFor()
                } catch (e: Exception) {
                    Log.w(
                        "CommandRunner",
                        "Failed to re-enable Quick Share",
                        e
                    )
                }
            }
        }
    }
}
