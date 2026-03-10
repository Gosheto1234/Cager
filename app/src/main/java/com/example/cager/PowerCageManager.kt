package com.example.cager

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log

object PowerCageManager {
    private const val TAG = "PowerCageManager"

    private const val SNAP_LOW_POWER = "snap_low_power"
    private const val SNAP_WIFI_SCAN = "snap_wifi_scan"
    private const val SNAP_BLE_SCAN = "snap_ble_scan"
    private const val SNAP_BRIGHTNESS = "snap_brightness"
    private const val SNAP_BRIGHTNESS_MODE = "snap_brightness_mode"
    private const val SNAP_AOD_MODE = "snap_aod_mode"
    private const val SNAP_DOZE_ALWAYS_ON = "snap_doze_always_on"
    private const val SNAP_NETWORK_MODE = "snap_network_mode"
    private const val SNAP_NETWORK_MODE1 = "snap_network_mode1"
    private const val SNAP_DEVICE_IDLE_CONSTANTS = "snap_device_idle_constants"

    fun apply(context: Context) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MainActivity.POWER_KEY, true)) {
            Log.d(TAG, "apply: power module disabled in prefs")
            return
        }

        Log.d(TAG, "apply: snapshotting and applying power profile")

        snapshotIfMissing(context, "global", "low_power", SNAP_LOW_POWER)
        snapshotIfMissing(context, "global", "wifi_scan_always_enabled", SNAP_WIFI_SCAN)
        snapshotIfMissing(context, "global", "ble_scan_always_enabled", SNAP_BLE_SCAN)
        snapshotIfMissing(context, "system", "screen_brightness", SNAP_BRIGHTNESS)
        snapshotIfMissing(context, "system", "screen_brightness_mode", SNAP_BRIGHTNESS_MODE)
        snapshotIfMissing(context, "system", "aod_mode", SNAP_AOD_MODE)
        snapshotIfMissing(context, "secure", "doze_always_on", SNAP_DOZE_ALWAYS_ON)
        snapshotIfMissing(context, "global", "preferred_network_mode", SNAP_NETWORK_MODE)
        snapshotIfMissing(context, "global", "preferred_network_mode1", SNAP_NETWORK_MODE1)
        snapshotIfMissing(context, "global", "device_idle_constants", SNAP_DEVICE_IDLE_CONSTANTS)

        if (prefs.getBoolean(MainActivity.POWER_HIDDEN_SCAN_KEY, true)) {
            Log.d(TAG, "apply: disabling hidden Wi-Fi/BLE scanning")
            runRoot("settings put global wifi_scan_always_enabled 0")
            runRoot("settings put global ble_scan_always_enabled 0")
        }

        if (prefs.getBoolean(MainActivity.POWER_BATTERY_SAVER_KEY, true)) {
            Log.d(TAG, "apply: enabling Battery Saver")
            runRoot("settings put global low_power 1")
        }

        if (prefs.getBoolean(MainActivity.POWER_AOD_KEY, true)) {
            Log.d(TAG, "apply: disabling AOD / doze always on")
            runRoot("settings put system aod_mode 0")
            runRoot("settings put secure doze_always_on 0")
        }

        if (prefs.getBoolean(MainActivity.POWER_BRIGHTNESS_KEY, true)) {
            val current = readSetting("system", "screen_brightness")?.toIntOrNull() ?: 128
            val reduced = (current - 26).coerceAtLeast(15)
            Log.d(TAG, "apply: reducing brightness from $current to $reduced")
            runRoot("settings put system screen_brightness_mode 0")
            runRoot("settings put system screen_brightness $reduced")
        }

        if (prefs.getBoolean(MainActivity.POWER_5G_KEY, false)) {
            Log.d(TAG, "apply: trying LTE-only / disable 5G")
            tryDisable5g()
        }

        reevaluate(context)
    }

    fun restore(context: Context) {
        Log.d(TAG, "restore: restoring power profile snapshot")
        exitStrictDoze(context)

        restoreSetting(context, "global", "low_power", SNAP_LOW_POWER)
        restoreSetting(context, "global", "wifi_scan_always_enabled", SNAP_WIFI_SCAN)
        restoreSetting(context, "global", "ble_scan_always_enabled", SNAP_BLE_SCAN)
        restoreSetting(context, "system", "screen_brightness", SNAP_BRIGHTNESS)
        restoreSetting(context, "system", "screen_brightness_mode", SNAP_BRIGHTNESS_MODE)
        restoreSetting(context, "system", "aod_mode", SNAP_AOD_MODE)
        restoreSetting(context, "secure", "doze_always_on", SNAP_DOZE_ALWAYS_ON)
        restoreSetting(context, "global", "preferred_network_mode", SNAP_NETWORK_MODE)
        restoreSetting(context, "global", "preferred_network_mode1", SNAP_NETWORK_MODE1)
        restoreSetting(context, "global", "device_idle_constants", SNAP_DEVICE_IDLE_CONSTANTS)

        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(SNAP_LOW_POWER)
            .remove(SNAP_WIFI_SCAN)
            .remove(SNAP_BLE_SCAN)
            .remove(SNAP_BRIGHTNESS)
            .remove(SNAP_BRIGHTNESS_MODE)
            .remove(SNAP_AOD_MODE)
            .remove(SNAP_DOZE_ALWAYS_ON)
            .remove(SNAP_NETWORK_MODE)
            .remove(SNAP_NETWORK_MODE1)
            .remove(SNAP_DEVICE_IDLE_CONSTANTS)
            .apply()
    }

    fun reevaluate(context: Context) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)

        val isCaged = prefs.getBoolean(MainActivity.IS_CAGED_KEY, false)
        val powerEnabled = prefs.getBoolean(MainActivity.POWER_KEY, true)
        val dozeEnabled = prefs.getBoolean(MainActivity.POWER_DOZE_KEY, true)

        if (!isCaged || !powerEnabled || !dozeEnabled) {
            Log.d(
                TAG,
                "reevaluate: strict doze disabled " +
                        "(isCaged=$isCaged, powerEnabled=$powerEnabled, dozeEnabled=$dozeEnabled)"
            )
            exitStrictDoze(context)
            return
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = pm?.isInteractive == true
        val charging = isCharging(context)

        Log.d(
            TAG,
            "reevaluate: isCaged=$isCaged powerEnabled=$powerEnabled dozeEnabled=$dozeEnabled " +
                    "interactive=$interactive charging=$charging"
        )

        if (interactive) {
            Log.d(TAG, "reevaluate: screen is on -> restoring normal doze timings")
            exitStrictDoze(context)
        } else if (charging) {
            Log.d(TAG, "reevaluate: charging -> restoring normal doze timings")
            exitStrictDoze(context)
        } else {
            Log.d(TAG, "reevaluate: screen off and not charging -> enabling strict doze timings")
            enterStrictDoze(context)
        }
    }

    private fun enterStrictDoze(context: Context) {
        snapshotIfMissing(context, "global", "device_idle_constants", SNAP_DEVICE_IDLE_CONSTANTS)

        val constants = buildStrictDozeConstants()
        Log.d(TAG, "enterStrictDoze: applying strict device_idle_constants = $constants")

        runRoot("settings put global device_idle_constants ${shellQuote(constants)}")
        runRoot("dumpsys deviceidle unforce")
        runRoot("dumpsys battery reset")
    }

    private fun exitStrictDoze(context: Context) {
        Log.d(TAG, "exitStrictDoze: restoring original doze timings")
        restoreSetting(context, "global", "device_idle_constants", SNAP_DEVICE_IDLE_CONSTANTS)
        runRoot("dumpsys deviceidle unforce")
        runRoot("dumpsys battery reset")
    }

    private fun buildStrictDozeConstants(): String {
        return listOf(
            "light_after_inactive_to=60000",
            "light_pre_idle_to=15000",
            "light_idle_to=120000",
            "light_idle_factor=2.0",
            "light_max_idle_to=600000",
            "inactive_to=300000",
            "sensing_to=60000",
            "locating_to=15000",
            "location_accuracy=20.0",
            "motion_inactive_to=120000",
            "idle_after_inactive_to=120000",
            "idle_pending_to=30000",
            "max_idle_pending_to=120000",
            "idle_pending_factor=2.0",
            "quick_doze_delay_to=10000",
            "idle_to=1800000",
            "max_idle_to=21600000",
            "idle_factor=2.5",
            "min_light_maintenance_time=3000",
            "min_deep_maintenance_time=10000",
            "min_time_to_alarm=60000"
        ).joinToString(",")
    }

    private fun isCharging(context: Context): Boolean {
        return try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return false

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            Log.d(TAG, "isCharging: status=$status charging=$charging")
            charging
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read charging state", e)
            false
        }
    }

    private fun tryDisable5g() {
        runRoot("settings put global preferred_network_mode 9")
        runRoot("settings put global preferred_network_mode1 9")
    }

    private fun snapshotIfMissing(
        context: Context,
        table: String,
        key: String,
        prefKey: String
    ) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(prefKey)) return

        val value = readSetting(table, key)
        Log.d(TAG, "snapshotIfMissing: $table/$key -> ${value ?: "null"}")
        prefs.edit().putString(prefKey, value ?: "null").apply()
    }

    private fun restoreSetting(
        context: Context,
        table: String,
        key: String,
        prefKey: String
    ) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(prefKey)) return

        val value = prefs.getString(prefKey, "null")
        Log.d(TAG, "restoreSetting: $table/$key <- ${value ?: "null"}")

        if (value == null || value == "null" || value.isBlank()) {
            runRoot("settings delete $table $key")
        } else {
            runRoot("settings put $table $key ${shellQuote(value)}")
        }
    }

    private fun readSetting(table: String, key: String): String? {
        return try {
            val proc = ProcessBuilder("su", "-c", "settings get $table $key")
                .redirectErrorStream(true)
                .start()

            val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
            proc.waitFor()

            if (out.isBlank() || out == "null") null else out
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading $table/$key", e)
            null
        }
    }

    private fun runRoot(cmd: String): Boolean {
        return try {
            Log.d(TAG, "runRoot: $cmd")

            val proc = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()

            val output = proc.inputStream.bufferedReader().use { it.readText() }.trim()
            val exit = proc.waitFor()

            if (exit != 0) {
                Log.w(TAG, "Command failed ($exit): $cmd :: $output")
                false
            } else {
                if (output.isNotBlank()) {
                    Log.d(TAG, "Command output: $output")
                }
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Command exception: $cmd", e)
            false
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}