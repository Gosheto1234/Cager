package com.example.cager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.BufferedReader

object ModuleLoader {

    // the exact sequence we want:
    private val MODULE_ORDER = listOf(
        "net",      // network lockdown
        "radios",   // wifi/data/bluetooth/nfc
        "airplane", // airplane mode
        "usb",      // USB off
        "sync",     // master sync
        "power",    // keep entry for compatibility, even if JSON is empty
        "apps",     // package enables/disables
        "sensors",  // camera/location/etc
        "extras"    // jobscheduler, caches, fstrim
    )

    /**
     * Returns a list-of-lists of commands, in MODULE_ORDER, filtered by prefs.
     */
    fun load(
        ctx: Context,
        prefs: SharedPreferences,
        disable: Boolean
    ): List<List<String>> {
        val selected = mutableListOf<List<String>>()

        for (id in MODULE_ORDER) {
            // commands are in assets/commands/<id>.json
            val path = "commands/$id.json"
            try {
                val text = ctx.assets.open(path)
                    .bufferedReader()
                    .use(BufferedReader::readText)

                val obj = JSONObject(text)
                val arr = obj.getJSONArray(if (disable) "disable" else "restore")

                val cmds = (0 until arr.length()).map { i ->
                    arr.getString(i)
                }

                if (prefs.getBoolean(id, true)) {
                    selected += cmds
                }
            } catch (_: Exception) {
                // missing file or JSON error -> skip
            }
        }

        // whitelist iptables rules first
        val whiteList = prefs.getStringSet(MainActivity.WHITELIST_KEY, emptySet())!!
        if (whiteList.isNotEmpty()) {
            val allowRules = whiteList.map { uid ->
                "iptables -A OUTPUT -m owner --uid-owner $uid -j ACCEPT"
            }
            selected.add(0, allowRules)
        }

        return selected
    }
}