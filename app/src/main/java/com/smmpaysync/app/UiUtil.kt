package com.smmpaysync.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiUtil {

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** level: 0 good, 1 warning, 2 bad, else neutral */
    fun levelColor(ctx: Context, level: Int): Int = ContextCompat.getColor(
        ctx,
        when (level) {
            0 -> R.color.success
            1 -> R.color.warn
            2 -> R.color.danger
            else -> R.color.text
        }
    )

    fun statusRow(ctx: Context, label: String, value: String, level: Int): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(ctx, 7), 0, dp(ctx, 7))

        val l = TextView(ctx)
        l.text = label
        l.textSize = 14f
        l.setTextColor(ContextCompat.getColor(ctx, R.color.muted))
        row.addView(l, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val v = TextView(ctx)
        v.text = value
        v.textSize = 14f
        v.gravity = Gravity.END
        v.setTextColor(levelColor(ctx, level))
        v.setTypeface(v.typeface, android.graphics.Typeface.BOLD)
        row.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
        return row
    }

    fun money(d: Double): String = String.format(Locale.US, "Tk %,.2f", d)

    fun dateTime(ms: Long): String =
        SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(ms))

    fun statusLabel(s: String): String = when (s) {
        St.USED -> "Used / Success"
        St.SYNCED -> "Sent to server - waiting for customer"
        St.DUPLICATE -> "On server - waiting for customer"
        St.FLAGGED -> "Flagged (admin review)"
        St.PENDING -> "Not sent yet"
        St.FAILED -> "Failed"
        St.REJECTED -> "Rejected by server"
        else -> s
    }

    fun statusLevel(s: String): Int = when (s) {
        St.USED -> 0
        St.PENDING, St.FLAGGED, St.SYNCED, St.DUPLICATE -> 1
        else -> 2
    }
}
