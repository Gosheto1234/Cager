package com.example.cager

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.*
import android.view.Menu
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private var isCaged = false
    private val uiScope = MainScope()
    private var asciiAnimator: ValueAnimator? = null

    companion object {
        const val PREFS = "cager_prefs"
        const val ROOT_CHANNEL_ID = "cager_root_channel"
        const val ROOT_NOTIFICATION_ID = 42

        const val ASCII_ANIM_KEY = "disableAsciiAnim"

        // <-- add "airplane" and "extras" here
        private val KEYS = listOf(
            "net",
            "radios",
            "airplane",
            "usb",
            "sync",
            "apps",
            "sensors",
            "extras"
        )

        const val WHITELIST_KEY = "whitelist"
        const val NOTIF_STYLE_KEY = "notifStyle"
        const val BATTERY_GUARD_KEY = "batteryGuard"
    }


    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService(findViewById(R.id.btnToggle))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        // ensure battery‑guard default
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.contains(BATTERY_GUARD_KEY)) {
            prefs.edit().putBoolean(BATTERY_GUARD_KEY, true).apply()
        }

        createRootNotificationChannel()
        requestNotificationPermissionIfNeeded()

        val btn = findViewById<Button>(R.id.btnToggle)
        val loader = findViewById<View>(R.id.loaderOverlay)
        val ascii = findViewById<TextView>(R.id.asciiLoader)
        ascii.text = loadAsciiArt()

        btn.setOnClickListener {
            if (!isCaged) {
                if (isDeviceRooted()) {
                    // ─── Root path ────────────────────────────────────────────
                    val svc = Intent(this, RootNotifyService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        startForegroundService(svc)
                    else
                        startService(svc)

                    uiScope.launch {
                        showLoader(loader, ascii)
                        try {
                            // Probe for working su
                            val probe = Runtime.getRuntime()
                                .exec(arrayOf("su", "-c", "echo granted"))
                                .inputStream
                                .bufferedReader()
                                .readLine()
                            if (probe != "granted") {
                                throw IllegalStateException("root probe failed")
                            }

                            // Run your JSON‑driven disable commands
                            CommandRunner.runCommands(this@MainActivity, true)

                            // update UI state
                            onCaged(true, btn)

                        } catch (e: Exception) {
                            Log.e("Cager", "root commands failed", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to obtain root or run commands.\n" +
                                            "Make sure your device is rooted and you granted su.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            hideLoader(loader)
                        }
                    }
                } else {
                    // ─── VPN path ────────────────────────────────────────────
                    val vpn = VpnService.prepare(this)
                    if (vpn != null) {
                        vpnPermissionLauncher.launch(vpn)
                    } else {
                        startVpnService(btn)
                        onCaged(true, btn)
                    }
                }
            } else {
                // ─── Uncage ────────────────────────────────────────────────
                if (isDeviceRooted()) {
                    uiScope.launch {
                        showLoader(loader, ascii)
                        try {
                            CommandRunner.runCommands(this@MainActivity, false)
                            onCaged(false, btn)

                            // stop the root‐mode notification service
                            stopService(Intent(this@MainActivity, RootNotifyService::class.java))

                        } catch (e: Exception) {
                            Log.e("Cager", "restore failed", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to restore systems.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            hideLoader(loader)
                        }
                    }
                } else {
                    // stop VPN service
                    stopService(Intent(this, CageVpnService::class.java))
                    onCaged(false, btn)
                }
            }
        }
    }


        private suspend fun showLoader(loader: View, ascii: TextView) = withContext(Dispatchers.Main) {
        loader.visibility = View.VISIBLE
        loader.startAnimation(AlphaAnimation(0f,1f).apply{duration=200})
        startAsciiGlitch(ascii)
    }

    private suspend fun hideLoader(loader: View) = withContext(Dispatchers.Main) {
        loader.startAnimation(AlphaAnimation(1f,0f).apply{duration=200})
        loader.visibility = View.GONE
    }

    private fun onCaged(caged: Boolean, btn: Button) {
        isCaged = caged
        btn.text = getString(if (caged) R.string.disable_cage_root else R.string.enable_cage)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_advanced) {
            showSettings()
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun showSettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val container = layoutInflater.inflate(R.layout.dialog_settings, null)
        val settingsLayout = container.findViewById<LinearLayout>(R.id.settings_container)

        // Subsystem toggles
        KEYS.forEach { key ->
            val sw = SwitchCompat(this).apply {
                text = key.capitalize()
                isChecked = prefs.getBoolean(key, true)
            }
            settingsLayout.addView(sw)
            sw.setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
                prefs.edit().putBoolean(key, on).apply()
            }
        }
        // ASCII animation switch
        val swAscii = SwitchCompat(this).apply {
            text = "Disable ASCII animation"
            isChecked = prefs.getBoolean(ASCII_ANIM_KEY, false)
        }
        settingsLayout.addView(swAscii)
        swAscii.setOnCheckedChangeListener { _, off ->
            prefs.edit().putBoolean(ASCII_ANIM_KEY, off).apply()
        }

        // Whitelist picker
        container.findViewById<Button>(R.id.btnWhitelist).setOnClickListener {
            startActivity(Intent(this, WhitelistActivity::class.java))
        }

        // Notification style radios
        val styleGroup = container.findViewById<RadioGroup>(R.id.radioNotif)
        listOf("full","icon","silent").forEachIndexed { i, tag ->
            styleGroup.addView(RadioButton(this).apply {
                text = tag
                id = i
                isChecked = prefs.getString(NOTIF_STYLE_KEY,"full")==tag
            })
        }
        styleGroup.setOnCheckedChangeListener { _, id ->
            prefs.edit()
                .putString(NOTIF_STYLE_KEY, listOf("full","icon","silent")[id])
                .apply()
        }

        // Battery guard switch
        val bg = container.findViewById<SwitchCompat>(R.id.swBattery)
        bg.isChecked = prefs.getBoolean(BATTERY_GUARD_KEY,true)
        bg.setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
            prefs.edit().putBoolean(BATTERY_GUARD_KEY,on).apply()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // Original helpers below:

    private fun loadAsciiArt(): String =
        resources.openRawResource(R.raw.ascii_art).bufferedReader().use { it.readText() }

    private fun startVpnService(btn: Button) {
        startService(Intent(this, CageVpnService::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    private fun isDeviceRooted(): Boolean = try {
        File("/system/xbin/su").exists() ||
                File("/system/bin/su").exists() ||
                Runtime.getRuntime().exec(arrayOf("which","su"))
                    .inputStream.bufferedReader().readText().isNotBlank()
    } catch (e: Exception) { false }

    private fun startAsciiGlitch(ascii: TextView) {
        asciiAnimator?.cancel()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(ASCII_ANIM_KEY, false)) {
            // user asked to disable ASCII animation → do nothing
            return
        }
        val original = ascii.text.toString()
        asciiAnimator = ValueAnimator.ofInt(0, original.length).apply {
            duration = 3000
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val sb = StringBuilder(original)
                repeat(2) {
                    sb.setCharAt(Random.nextInt(original.length), listOf('█','░').random())
                }
                ascii.text = sb
            }
            start()
        }
    }

    private fun createRootNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                ROOT_CHANNEL_ID,
                getString(R.string.root_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.root_channel_description)
            }.also { chan ->
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(chan)
            }
        }
    }
}

class RootNotifyService : Service() {
    override fun onStartCommand(intent: Intent?, serviceFlags: Int, startId: Int): Int {
        val note = NotificationCompat.Builder(this, MainActivity.ROOT_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
            .apply {
                // Now there’s no shadowing: 'serviceFlags' is your method param
                // but here we’re assigning to the Notification’s flags property:
                flags = flags or
                        Notification.FLAG_ONGOING_EVENT or
                        Notification.FLAG_NO_CLEAR
            }

        startForeground(MainActivity.ROOT_NOTIFICATION_ID, note)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
