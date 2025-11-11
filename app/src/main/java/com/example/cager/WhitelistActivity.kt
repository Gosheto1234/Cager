package com.example.cager

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WhitelistActivity : AppCompatActivity() {
    private lateinit var rvApps: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whitelist)

        rvApps = findViewById(R.id.rvApps)
        rvApps.layoutManager = LinearLayoutManager(this)

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val prefs = getSharedPreferences("cager_prefs", MODE_PRIVATE)
        val current = prefs.getStringSet("whitelist", emptySet())!!.toMutableSet()

        rvApps.adapter = AppListAdapter(apps, current) { updatedSet: MutableSet<String> ->
            prefs.edit { putStringSet("whitelist", updatedSet) }
        }
    }
}
