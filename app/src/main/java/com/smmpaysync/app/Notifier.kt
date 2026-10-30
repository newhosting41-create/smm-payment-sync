package com.smmpaysync.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifier {
    const val CHANNEL = "monitor"
    const val ID = 1001

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "Payment monitor", NotificationManager.IMPORTANCE_LOW)
                ch.description = "Keeps the SMS payment monitor running"
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun build(ctx: Context): Notification {
        ensureChannel(ctx)
        val prefs = Prefs(ctx)
        val db = Db.get(ctx)

        val pending = db.count(listOf(St.PENDING, St.FAILED, St.REJECTED))
        val waiting = db.count(St.onServer)
        val used = db.count(St.success)
        val last = prefs.lastSync

        val title: String
        val text: String
        if (!prefs.isConfigured) {
            title = "Setup incomplete"
            text = "Open the app > Device Settings and save URL, Token, Secret."
        } else {
            title = "Payment monitor is running"
            text = "Not sent: $pending  |  Waiting: $waiting  |  Used: $used" +
                (if (last > 0) "  |  Last sync " + UiUtil.dateTime(last) else "")
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_send)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    fun update(ctx: Context) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(ID, build(ctx.applicationContext))
        } catch (e: Exception) {
        }
    }
}
