package com.example.cager

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class CagerAccessibilityService : AccessibilityService() {
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
        val cls = event.className?.toString().orEmpty()

        when {
            toggleBattery && cls.contains("Battery", ignoreCase = true) -> {
                if (clickFirstSwitch(root)) {
                    toggleBattery = false
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }

            toggleNfc && cls.contains("Nfc", ignoreCase = true) -> {
                if (clickFirstSwitch(root)) {
                    toggleNfc = false
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }

            toggleAirplane &&
                    (cls.contains("Airplane", ignoreCase = true)
                            || cls.contains("Wireless", ignoreCase = true)) -> {
                if (clickFirstSwitch(root)) {
                    toggleAirplane = false
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    private fun clickFirstSwitch(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className == "android.widget.Switch" && node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }

        return false
    }

    override fun onInterrupt() {}
}