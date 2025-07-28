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

object RadioToggler {
    fun setWifi(ctx: Context, on: Boolean) {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            wm.isWifiEnabled = on
        } else {
            // Q+ cannot toggle programmatically; show the panel
            val panel = Intent(Settings.Panel.ACTION_WIFI)
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            ctx.startActivity(panel)
        }
    }

    fun setBluetooth(ctx: Context, on: Boolean) {
        val bt = BluetoothAdapter.getDefaultAdapter() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val has = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!has) {
                // Fallback to settings if permission is missing
                ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                return
            }
        }

        if (on) bt.enable() else bt.disable()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bt.isEnabled != on) {
            ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    fun openNfcSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun openAirplaneSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
