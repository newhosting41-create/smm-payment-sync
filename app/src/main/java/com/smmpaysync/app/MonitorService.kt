package com.smmpaysync.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground service: keeps the app alive (persistent notification), sends pending
 * SMS as soon as internet is available, and checks which ones were used on the website.
 */
class MonitorService : Service() {

    private var exec: ScheduledExecutorService? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.build(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID, n)
        }

        if (exec == null) {
            val e = Executors.newSingleThreadScheduledExecutor()
            exec = e
            e.scheduleWithFixedDelay({ SyncRunner.runOnce(applicationContext) }, 3, 30, TimeUnit.SECONDS)

            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        // Internet is back: send everything waiting right now.
                        exec?.execute { SyncRunner.runOnce(applicationContext) }
                    }
                }
                cm.registerDefaultNetworkCallback(cb)
                callback = cb
            } catch (ex: Exception) {
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            callback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
        }
        callback = null
        exec?.shutdownNow()
        exec = null
        super.onDestroy()
    }

    companion object {
        /** Start (or keep) the service if the user has it enabled. Safe to call many times. */
        fun ensure(ctx: Context) {
            val app = ctx.applicationContext
            try {
                if (Prefs(app).monitor) {
                    ContextCompat.startForegroundService(app, Intent(app, MonitorService::class.java))
                }
            } catch (e: Exception) {
                // Android may refuse to start it from the background; it will start when the app is opened.
            }
        }

        fun stop(ctx: Context) {
            val app = ctx.applicationContext
            app.stopService(Intent(app, MonitorService::class.java))
        }
    }
}
