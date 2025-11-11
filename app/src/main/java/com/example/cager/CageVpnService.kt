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
import android.util.Log

class CageVpnService : VpnService(), CoroutineScope {
    companion object {
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val VPN_CHANNEL_ID = "cage_service"
        const val ACTION_STOP = "com.example.cager.ACTION_STOP_VPN"
    }

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.IO + job

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("CageVpnService","onStartCommand")
        startForeground(ONGOING_NOTIFICATION_ID, buildNotification())
        establishVpn()
        return START_STICKY
    }


    private fun establishVpn() {
        // Build a VPN that captures all traffic
        val builder = Builder().apply {
            setMtu(1500)
            addAddress("10.0.0.2", 32)
            addRoute("0.0.0.0", 0)
        }

        // This is your “tunnel” file descriptor
        vpnInterface = builder.establish()
        vpnInterface?.let { fd ->
            launch {
                val inputChannel = FileInputStream(fd.fileDescriptor).channel
                val packet = ByteBuffer.allocate(32767)

                while (isActive) {
                    packet.clear()
                    // read() will block until something arrives
                    val length = inputChannel.read(packet)
                    if (length > 0) {
                        // we simply drop every outbound packet
                    }
                }
            }
        }
    }

    /**
     * If the user disables the VPN in system settings, this will be called.
     * We tear down immediately.
     */
    override fun onRevoke() {
        Log.d("CageVpnService","onRevoke")
        vpnInterface?.close()
        stopForeground(true)
        stopSelf()
    }


    override fun onDestroy() {
        Log.d("CageVpnService","onDestroy")
        job.cancel()
        vpnInterface?.close()
        stopForeground(true)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        createNotificationChannelIfNeeded()
        return NotificationCompat.Builder(this, VPN_CHANNEL_ID)
            .setContentTitle("Cage is active")
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)     // sticky
            .setAutoCancel(false) // never auto‑cancel
            .build()
            .apply {
                // enforce the flags on the Notification object
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
