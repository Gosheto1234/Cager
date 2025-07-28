package com.example.cager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result

class AutoToggleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Wi‑Fi: API21+ call with pre‑23 getSystemService
        val wifiMgr = applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val wantWifi = inputData.getBoolean("wifiOn", false)
        if (wifiMgr != null) {
            val hasWifiPerm = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.CHANGE_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (hasWifiPerm) {
                try {
                    wifiMgr.isWifiEnabled = wantWifi
                } catch (e: SecurityException) {
                    // permission revoked at runtime—skip
                }
            }
        }

        // Bluetooth: guard with CONNECT/Admin permission
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        val wantBt = inputData.getBoolean("btOn", false)
        val hasBtPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (btAdapter != null && hasBtPerm) {
            try {
                if (wantBt) btAdapter.enable() else btAdapter.disable()
            } catch (e: SecurityException) {
                // skip if user revoked
            }
        }

        // NFC & Airplane: fire Settings Intents (no permissions)
        if (inputData.getBoolean("openNfcSettings", false)) {
            applicationContext.startActivity(
                Intent(Settings.ACTION_NFC_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        if (inputData.getBoolean("openAirplaneSettings", false)) {
            applicationContext.startActivity(
                Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        return Result.success()
    }
}
