package com.smmpaysync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restart monitoring after phone reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            MonitorService.ensure(context)
            SyncWorker.schedulePeriodic(context)
        }
    }
}
