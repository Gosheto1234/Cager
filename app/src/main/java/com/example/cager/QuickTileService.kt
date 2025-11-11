// QuickTileService.kt
package com.example.cager

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // Build the PendingIntent (always available)
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(this, 0, intent, piFlags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: call the new overload directly
            startActivityAndCollapse(pending)
        } else {
            // API 24–33: invoke the old Intent version via reflection so we never reference it directly
            try {
                val m = TileService::class.java.getMethod("startActivityAndCollapse", Intent::class.java)
                m.invoke(this, intent)
            } catch (e: Exception) {
                // fallback to a normal launch if reflection fails
                startActivity(intent)
                collapse()  // quietly collapse the QS panel
            }
        }
    }

    // helper to collapse the tile panel manually if needed
    private fun collapse() {
        try {
            val svc = Class.forName("android.service.quicksettings.TileService")
            val m = svc.getMethod("onStopListening")
            m.invoke(this)
        } catch (_: Exception) { }
    }
}
