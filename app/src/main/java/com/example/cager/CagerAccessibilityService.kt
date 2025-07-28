package com.example.cager

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CagerAccessibilityService : AccessibilityService() {
    // flags set by MainActivity
    companion object {
        @Volatile var toggleNfc = false
        @Volatile var toggleAirplane = false

        @Volatile var toggleBattery = false
    }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = arrayOf("com.android.settings")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        val cls = event.className?.toString() ?: return

        when {
            cls.contains("NfcSettings") && toggleNfc -> {
                clickSwitch(root, "NFC")
                toggleNfc = false
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            cls.contains("WirelessSettings$") && toggleNfc -> {
                // Some OEMs use a different class name
                clickSwitch(root, "NFC")
                toggleNfc = false
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            cls.contains("AirplaneModeSettings") && toggleAirplane -> {
                clickSwitch(root, "Airplane")
                toggleAirplane = false
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun clickSwitch(root: AccessibilityNodeInfo, text: String) {
        root.findAccessibilityNodeInfosByText(text).forEach { node ->
            // climb up to the Switch
            var p: AccessibilityNodeInfo? = node
            while (p != null && p.className != "android.widget.Switch") {
                p = p.parent
            }
            p?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onInterrupt() {}
}
