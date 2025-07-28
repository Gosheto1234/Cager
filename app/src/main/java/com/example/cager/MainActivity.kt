package com.example.cager

// Android + Kotlin stdlib
import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import androidx.work.Data
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.*
import android.provider.Settings as ProvSettings
import android.util.Log
import android.view.Menu
import androidx.work.PeriodicWorkRequest.Builder
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// **WorkManager imports – NO wildcards, NO KTX builder**
import androidx.work.PeriodicWorkRequest
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager


class MainActivity : AppCompatActivity() {
    private var isCaged = false
    private val uiScope = MainScope()
    private var asciiAnimator: ValueAnimator? = null

    companion object {
        const val PREFS = "cager_prefs"
        const val ROOT_CHANNEL_ID = "cager_root_channel"
        const val ROOT_NOTIFICATION_ID = 42
        const val ASCII_ANIM_KEY = "disableAsciiAnim"
        private val KEYS = listOf(
            "net", "radios", "airplane", "usb", "sync", "apps", "sensors", "extras"
        )
        const val WHITELIST_KEY = "whitelist"
        const val NOTIF_STYLE_KEY = "notifStyle"
        const val BATTERY_GUARD_KEY = "batteryGuard"

        private const val REQUEST_BT_WIFI = 1002

        @Volatile var toggleNfc = false
        @Volatile var toggleAirplane = false
        @Volatile var toggleBattery = false
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // User granted the VPN permission → start the service
            onCaged(true, findViewById(R.id.btnToggle))
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
        ensureBluetoothPerms()

        // ─── Schedule periodic housekeeping ────────────────────────
        val housekeeperReq: PeriodicWorkRequest = Builder(
            HousekeeperWorker::class.java,
            12L,
            TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "housekeeper",
                ExistingPeriodicWorkPolicy.KEEP,
                housekeeperReq
            )


        val btn = findViewById<Button>(R.id.btnToggle)
        val loader = findViewById<View>(R.id.loaderOverlay)
        val ascii = findViewById<TextView>(R.id.asciiLoader)
        ascii.text = loadAsciiArt()

        btn.setOnClickListener {
            if (!isCaged) {
                // ─── ENABLE CAGE ───────────────────────────────────────
                if (isDeviceRooted()) {
                    // ─ Rooted enable ────────────────────────
                    uiScope.launch {
                        showLoader(loader, ascii)
                        try {
                            CommandRunner.runCommands(this@MainActivity, true)
                            onCaged(true, btn)
                        } catch (e: Exception) {
                            Log.e("Cager", "root enable failed", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to run root commands.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            hideLoader(loader)
                        }
                    }
                } else {
                    // ─ Non‑root enable ───────────────────────
                    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                    val canWifi = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.CHANGE_WIFI_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                    val canBt = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED

                    if (canWifi) wifiManager.isWifiEnabled = false
                    bluetoothAdapter?.takeIf { canBt }?.disable()

                    if (prefs.getBoolean(BATTERY_GUARD_KEY, true)) {
                        CagerAccessibilityService.toggleBattery = true
                        startActivity(
                            Intent(ProvSettings.ACTION_BATTERY_SAVER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }

                    CagerAccessibilityService.toggleNfc = true
                    startActivity(
                        Intent(ProvSettings.ACTION_NFC_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )

                    CagerAccessibilityService.toggleAirplane = true
                    startActivity(
                        Intent(ProvSettings.ACTION_AIRPLANE_MODE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )

                    onCaged(true, btn)
                }
            } else {
                // ─── DISABLE CAGE ───────────────────────────────────────
                if (isDeviceRooted()) {
                    // ─ Rooted disable ────────────────────────
                    uiScope.launch {
                        showLoader(loader, ascii)
                        try {
                            CommandRunner.runCommands(this@MainActivity, false)
                            onCaged(false, btn)
                        } catch (e: Exception) {
                            Log.e("Cager", "root disable failed", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to disable root commands.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            hideLoader(loader)
                        }
                    }
                } else {
                    // ─ Non‑root disable ───────────────────────
                    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                    val canWifi = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.CHANGE_WIFI_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                    val canBt = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED

                    if (canWifi) wifiManager.isWifiEnabled = true
                    bluetoothAdapter?.takeIf { canBt }?.enable()

                    CagerAccessibilityService.toggleAirplane = true
                    startActivity(
                        Intent(ProvSettings.ACTION_AIRPLANE_MODE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )

                    CagerAccessibilityService.toggleNfc = true
                    startActivity(
                        Intent(ProvSettings.ACTION_NFC_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )

                    if (prefs.getBoolean(BATTERY_GUARD_KEY, true)) {
                        CagerAccessibilityService.toggleBattery = true
                        startActivity(
                            Intent(ProvSettings.ACTION_BATTERY_SAVER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }

                    onCaged(false, btn)
                }
            }
        }
    }

        // ─── Loader helpers ─────────────────────────────────────────
    private suspend fun showLoader(loader: View, ascii: TextView) = withContext(Dispatchers.Main) {
        loader.visibility = View.VISIBLE
        loader.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 200 })
        startAsciiGlitch(ascii)
    }

    private suspend fun hideLoader(loader: View) = withContext(Dispatchers.Main) {
        loader.startAnimation(AlphaAnimation(1f, 0f).apply { duration = 200 })
        loader.visibility = View.GONE
    }

    private fun ensureBluetoothPerms() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.CHANGE_WIFI_STATE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_BT_WIFI)
        }
    }



    // ─── Update UI & DND ────────────────────────────────────────
    private fun onCaged(caged: Boolean, btn: Button) {
        isCaged = caged
        btn.text = getString(if (caged) R.string.disable_cage else R.string.enable_cage)

        // DND logic, if you still want it:
        val style = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(NOTIF_STYLE_KEY, "full")
        if (caged && style == "silent") enableDndNone()
        else restoreDnd()
    }

    private fun enableDndNone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.isNotificationPolicyAccessGranted) {
                // Sends user to exactly the screen where your app will now be listed
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
        }
    }

    private fun restoreDnd() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(NotificationManager::class.java)
                .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    // ─── Menu & Advanced dialog ─────────────────────────────────
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_advanced) {
            showSettings(); true
        } else super.onOptionsItemSelected(item)
    }

    private val wifiManager: WifiManager by lazy {
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    private fun showSettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val container = layoutInflater.inflate(R.layout.dialog_settings, null)
        val settingsLayout = container.findViewById<LinearLayout>(R.id.settings_container)

        // Subsystem toggles:
        // Only show the toggles that will actually work:
        val availableKeys = if (isDeviceRooted()) {
            KEYS
        } else {
            listOf("radios", "airplane")
        }
        availableKeys.forEach { key ->
            val sw = SwitchCompat(this).apply {
                text = key.capitalize()
                isChecked = prefs.getBoolean(key, true)
            }
            settingsLayout.addView(sw)
            sw.setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
                prefs.edit().putBoolean(key, on).apply()
            }
        }
        // ASCII‑animation toggle:
        val swAscii = SwitchCompat(this).apply {
            text = getString(R.string.disable_animation)
            isChecked = prefs.getBoolean(ASCII_ANIM_KEY, false)
        }
        settingsLayout.addView(swAscii)
        swAscii.setOnCheckedChangeListener { _, off ->
            prefs.edit().putBoolean(ASCII_ANIM_KEY, off).apply()
        }

        // Re‑open notif access button:
        val btnNotif = Button(this).apply { text = getString(R.string.open_settings) }
        settingsLayout.addView(btnNotif)
        btnNotif.setOnClickListener { requestNotificationListenerPermission() }

        // Whitelist picker:
        container.findViewById<Button>(R.id.btnWhitelist)
            .setOnClickListener {
                startActivity(Intent(this, WhitelistActivity::class.java))
            }

        // Notification style radios:
        val styleGroup = container.findViewById<RadioGroup>(R.id.radioNotif)
        listOf("full", "icon", "silent").forEachIndexed { i, tag ->
            styleGroup.addView(RadioButton(this).apply {
                text = tag
                id = i
                isChecked = prefs.getString(NOTIF_STYLE_KEY, "full") == tag
            })
        }
        styleGroup.setOnCheckedChangeListener { _, id ->
            prefs.edit()
                .putString(NOTIF_STYLE_KEY, listOf("full", "icon", "silent")[id])
                .apply()
        }

        // Battery‑guard:
        val bg = container.findViewById<SwitchCompat>(R.id.swBattery)
        bg.isChecked = prefs.getBoolean(BATTERY_GUARD_KEY, true)
        bg.setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
            prefs.edit().putBoolean(BATTERY_GUARD_KEY, on).apply()
        }

        // ─── “Rooted: yes/no” footer ──────────────────────────
        val tvRooted = TextView(this).apply {
            text = if (isDeviceRooted()) "Rooted: yes" else "Rooted: no"
            setPadding(0, 16, 0, 0)
        }
        settingsLayout.addView(tvRooted)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ─── Notification listener helpers ─────────────────────────
    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val me = ComponentName(this, NotificationHandlerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun requestNotificationListenerPermission() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    // ─── Original helpers ───────────────────────────────────────
    private fun loadAsciiArt(): String =
        resources.openRawResource(R.raw.ascii_art).bufferedReader().use { it.readText() }

    /** Starts the Cage VPN service in the foreground. */



    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
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
                Runtime.getRuntime().exec(arrayOf("which", "su"))
                    .inputStream.bufferedReader().readText().isNotBlank()
    } catch (_: Exception) {
        false
    }

    private fun startAsciiGlitch(ascii: TextView) {
        asciiAnimator?.cancel()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(ASCII_ANIM_KEY, false)) return

        val original = ascii.text.toString()
        asciiAnimator = ValueAnimator.ofInt(0, original.length).apply {
            duration = 3000
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val sb = StringBuilder(original)
                repeat(2) {
                    sb.setCharAt(
                        Random.nextInt(original.length),
                        listOf('█', '░').random()
                    )
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
                getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(chan)
            }
        }
    }
}
