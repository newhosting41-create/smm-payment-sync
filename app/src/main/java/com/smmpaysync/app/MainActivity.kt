package com.smmpaysync.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var nav: NavigationView
    private lateinit var toolbar: Toolbar
    private var current = R.id.nav_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawer = findViewById(R.id.drawer)
        nav = findViewById(R.id.nav)
        toolbar = findViewById(R.id.toolbar)

        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }

        nav.setNavigationItemSelectedListener { item ->
            show(item.itemId)
            drawer.closeDrawer(GravityCompat.START)
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START)
                } else if (current != R.id.nav_dashboard) {
                    show(R.id.nav_dashboard)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        Thread {
            try {
                Db.get(this).purge(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
            } catch (e: Exception) {
            }
        }.start()

        SyncWorker.schedulePeriodic(this)

        val need = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
        }

        val start = savedInstanceState?.getInt("cur", R.id.nav_dashboard) ?: R.id.nav_dashboard
        if (savedInstanceState == null) {
            show(start)
        } else {
            current = start
            nav.setCheckedItem(current)
            toolbar.title = titleFor(current)
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorService.ensure(this)
        SyncRunner.trigger(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MonitorService.ensure(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("cur", current)
    }

    private fun titleFor(id: Int): String = when (id) {
        R.id.nav_success -> "Success"
        R.id.nav_pending -> "Pending / Not Sent"
        R.id.nav_manual -> "Send SMS Manually"
        R.id.nav_settings -> "Device Settings"
        R.id.nav_about -> "About"
        else -> "Dashboard"
    }

    fun show(id: Int) {
        val f: Fragment = when (id) {
            R.id.nav_success -> EventsFragment.newInstance(EventsFragment.MODE_SUCCESS)
            R.id.nav_pending -> EventsFragment.newInstance(EventsFragment.MODE_PENDING)
            R.id.nav_manual -> ManualFragment()
            R.id.nav_settings -> SettingsFragment()
            R.id.nav_about -> AboutFragment()
            else -> DashboardFragment()
        }
        current = id
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
        nav.setCheckedItem(id)
        toolbar.title = titleFor(id)
    }
}
