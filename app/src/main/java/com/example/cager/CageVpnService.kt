package com.example.cager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class CageVpnService : VpnService(), CoroutineScope {
    companion object {
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val VPN_CHANNEL_ID = "cage_service"
    }

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.IO + job

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_NOTIFICATION_ID, buildNotification())
        establishVpn()
        return START_STICKY
    }

    private fun establishVpn() {
        val builder = Builder().apply {
            setMtu(1500)
            addAddress("10.0.0.2", 32)   // Virtual IP
            addRoute("0.0.0.0", 0)       // Capture all traffic
            // You can call .addDnsServer() or .allowFamily() here as needed
        }

        vpnInterface = builder.establish()
        vpnInterface?.let { fd ->
            launch {
                val inputChannel = FileInputStream(fd.fileDescriptor).channel
                // val outputChannel = FileOutputStream(fd.fileDescriptor).channel
                val packet = ByteBuffer.allocate(32767)

                while (isActive) {
                    packet.clear()
                    val length = inputChannel.read(packet)
                    if (length > 0) {
                        // Drop every packet: outbound is blocked
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        job.cancel()
        vpnInterface?.close()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        createNotificationChannelIfNeeded()
        return NotificationCompat.Builder(this, VPN_CHANNEL_ID)
            .setContentTitle("Cage is active")
            .setSmallIcon(R.drawable.ic_lock)
            // ↓ Make it ongoing & non‑auto‑cancelable
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
            .apply {
                flags = flags or
                        Notification.FLAG_ONGOING_EVENT or
                        Notification.FLAG_NO_CLEAR
            }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                VPN_CHANNEL_ID,
                "Cage VPN Service",
                NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(NotificationManager::class.java))
                .createNotificationChannel(chan)
        }
    }
}
