package com.smmpaysync.app

import android.content.Context
import java.util.concurrent.Executors

/** Runs "send pending + check which ones were used" on a background thread. */
object SyncRunner {
    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sync-runner").also { it.isDaemon = true }
    }

    @Synchronized
    fun runOnce(ctx: Context) {
        val app = ctx.applicationContext
        try {
            if (UiUtil.isOnline(app)) {
                SyncEngine.run(app)
                SyncEngine.pollStatus(app)
            }
        } catch (e: Exception) {
        }
        Notifier.update(app)
    }

    fun trigger(ctx: Context) {
        val app = ctx.applicationContext
        exec.execute { runOnce(app) }
    }
}
