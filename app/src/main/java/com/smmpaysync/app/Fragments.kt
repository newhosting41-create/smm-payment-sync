package com.smmpaysync.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun Fragment.toast(msg: String) {
    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
}

private fun hasSms(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

/* ------------------------------------------------------------------ */
/* Dashboard                                                           */
/* ------------------------------------------------------------------ */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity

        v.findViewById<View>(R.id.cardSuccess).setOnClickListener { act.show(R.id.nav_success) }
        v.findViewById<View>(R.id.cardPending).setOnClickListener { act.show(R.id.nav_pending) }
        v.findViewById<View>(R.id.cardFailed).setOnClickListener { act.show(R.id.nav_pending) }

        v.findViewById<View>(R.id.btnSyncNow).setOnClickListener {
            if (!Prefs(ctx).isConfigured) {
                toast("Pair the device first (Device Settings)")
                return@setOnClickListener
            }
            toast("Sync started")
            SyncRunner.trigger(ctx)
            v.postDelayed({ if (isAdded) refresh() }, 3000)
        }
        v.findViewById<View>(R.id.btnTest).setOnClickListener {
            toast("Testing connection...")
            val app = ctx.applicationContext
            val a = requireActivity()
            Thread {
                val msg = SyncEngine.ping(app)
                a.runOnUiThread {
                    Toast.makeText(a, msg, Toast.LENGTH_LONG).show()
                    if (isAdded) refresh()
                }
            }.start()
        }
        v.findViewById<View>(R.id.btnPerm).setOnClickListener {
            val need = ArrayList<String>()
            if (!hasSms(ctx)) need.add(Manifest.permission.RECEIVE_SMS)
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) need.add(Manifest.permission.POST_NOTIFICATIONS)
            if (need.isEmpty()) toast("All permissions already granted")
            else ActivityCompat.requestPermissions(requireActivity(), need.toTypedArray(), 1)
        }
        v.findViewById<View>(R.id.btnAppInfo).setOnClickListener {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:" + ctx.packageName)
            startActivity(i)
        }
        v.findViewById<View>(R.id.btnBattery).setOnClickListener {
            try {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:" + ctx.packageName)
                startActivity(i)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e2: Exception) {
                    toast("Open phone Settings > Battery manually")
                }
            }
        }

        val sw = v.findViewById<SwitchMaterial>(R.id.swMonitor)
        sw.isChecked = Prefs(ctx).monitor
        sw.setOnCheckedChangeListener { _, checked ->
            Prefs(ctx).monitor = checked
            if (checked) MonitorService.ensure(ctx) else MonitorService.stop(ctx)
        }
    }

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (isAdded) {
                refresh()
                ui.postDelayed(this, 4000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ui.postDelayed(tick, 4000)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    private fun refresh() {
        val v = view ?: return
        val ctx = requireContext()
        val prefs = Prefs(ctx)
        val db = Db.get(ctx)

        val sms = hasSms(ctx)
        val configured = prefs.isConfigured
        val active = sms && configured

        v.findViewById<MaterialCardView>(R.id.heroCard).setCardBackgroundColor(
            ContextCompat.getColor(ctx, if (active) R.color.success else R.color.danger)
        )
        v.findViewById<TextView>(R.id.tvHeroState).text =
            if (active) "SMS Monitor: Active" else "SMS Monitor: Inactive"
        v.findViewById<TextView>(R.id.tvHeroSub).text = when {
            !sms -> "SMS permission missing. Tap \"SMS Permission\" below."
            !configured -> "Device not paired. Open Device Settings."
            else -> "Incoming payment SMS are captured and sent automatically."
        }

        v.findViewById<TextView>(R.id.tvSuccess).text = db.count(St.success).toString()
        v.findViewById<TextView>(R.id.tvPending).text =
            db.count(listOf(St.PENDING) + St.onServer).toString()
        v.findViewById<TextView>(R.id.tvFailed).text =
            db.count(listOf(St.FAILED, St.REJECTED)).toString()

        val box = v.findViewById<LinearLayout>(R.id.statusBox)
        box.removeAllViews()

        box.addView(UiUtil.statusRow(ctx, "SMS permission", if (sms) "Granted" else "Missing", if (sms) 0 else 2))
        box.addView(UiUtil.statusRow(ctx, "Device pairing", if (configured) "Configured" else "Not configured", if (configured) 0 else 2))

        val seenAt = prefs.seenAt
        box.addView(
            UiUtil.statusRow(
                ctx, "Last SMS seen",
                if (seenAt == 0L) "None yet" else UiUtil.dateTime(seenAt) + "\n" + prefs.seenInfo,
                if (seenAt == 0L) 1 else 0
            )
        )
        val notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        box.addView(UiUtil.statusRow(ctx, "Notifications", if (notifOn) "Allowed" else "Blocked - allow in App info", if (notifOn) 0 else 1))
        box.addView(UiUtil.statusRow(ctx, "Background service", if (prefs.monitor) "ON" else "OFF", if (prefs.monitor) 0 else 2))

        val online = UiUtil.isOnline(ctx)
        box.addView(UiUtil.statusRow(ctx, "Internet", if (online) "Connected" else "Offline (SMS wait in queue)", if (online) 0 else 1))

        val res = prefs.lastResult
        box.addView(
            UiUtil.statusRow(
                ctx, "Backend",
                if (res.isEmpty()) "Not tested yet" else res,
                if (res == "OK") 0 else if (res.isEmpty()) 1 else 2
            )
        )

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        box.addView(UiUtil.statusRow(ctx, "Battery", if (unrestricted) "Unrestricted" else "May be stopped by phone", if (unrestricted) 0 else 1))

        val last = prefs.lastSync
        box.addView(
            UiUtil.statusRow(
                ctx, "Last successful sync",
                if (last == 0L) "Never" else UiUtil.dateTime(last),
                if (last == 0L) 1 else 0
            )
        )

        val enabled = Providers.all.filter { prefs.providerEnabled(it) }.joinToString(", ") { it.label }
        box.addView(UiUtil.statusRow(ctx, "Watching", if (enabled.isEmpty()) "None" else enabled, if (enabled.isEmpty()) 2 else -1))
    }
}

/* ------------------------------------------------------------------ */
/* Success / Pending lists                                             */
/* ------------------------------------------------------------------ */
class EventsFragment : Fragment(R.layout.fragment_events) {

    companion object {
        const val MODE_SUCCESS = "success"
        const val MODE_PENDING = "pending"

        fun newInstance(mode: String): EventsFragment {
            val f = EventsFragment()
            f.arguments = Bundle().apply { putString("mode", mode) }
            return f
        }
    }

    private val mode: String get() = arguments?.getString("mode") ?: MODE_SUCCESS

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val btn = v.findViewById<View>(R.id.btnAction)
        if (mode == MODE_PENDING) {
            btn.visibility = View.VISIBLE
            btn.setOnClickListener { sendPending() }
        } else {
            btn.visibility = View.GONE
        }
    }

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (isAdded) {
                load()
                ui.postDelayed(this, 4000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
        SyncRunner.trigger(requireContext())
        ui.postDelayed(tick, 4000)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    private fun sendPending() {
        val ctx = requireContext()
        if (!Prefs(ctx).isConfigured) {
            toast("Pair the device first (Device Settings)")
            return
        }
        if (!UiUtil.isOnline(ctx)) {
            toast("Internet is OFF. Turn it on, then tap again.")
            return
        }
        toast("Sending...")
        val app = ctx.applicationContext
        val act = requireActivity()
        Thread {
            Db.get(app).retryFailed()
            val out = SyncEngine.run(app)
            SyncEngine.pollStatus(app)
            Notifier.update(app)
            act.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                load()
                toast(if (out == SyncEngine.Outcome.RETRY) "Server/network problem. Will retry automatically." else "Done")
            }
        }.start()
    }

    private fun load() {
        val v = view ?: return
        val ctx = requireContext()
        val db = Db.get(ctx)
        val statuses = if (mode == MODE_SUCCESS) St.success else St.waiting
        val items = db.listByStatus(statuses, 200)

        v.findViewById<TextView>(R.id.tvCount).text =
            (if (mode == MODE_SUCCESS) "Used / Success: " else "Pending: ") + items.size

        val list = v.findViewById<LinearLayout>(R.id.list)
        val empty = v.findViewById<TextView>(R.id.tvEmpty)
        list.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (mode == MODE_SUCCESS) "No used payments yet." else "Nothing pending. All good!"

        val inflater = LayoutInflater.from(ctx)
        for (e in items) {
            val row = inflater.inflate(R.layout.item_event, list, false)
            val p = Providers.byKey(e.provider)

            val badge = row.findViewById<TextView>(R.id.tvProvider)
            badge.text = p.label
            badge.backgroundTintList = ColorStateList.valueOf(p.color)

            row.findViewById<TextView>(R.id.tvAmount).text = UiUtil.money(e.amount)
            row.findViewById<TextView>(R.id.tvTrx).text = "TrxID  " + e.trxId

            val st = row.findViewById<TextView>(R.id.tvStatus)
            st.text = UiUtil.statusLabel(e.status)
            st.setTextColor(UiUtil.levelColor(ctx, UiUtil.statusLevel(e.status)))

            row.findViewById<TextView>(R.id.tvTime).text =
                if (e.smsTime.isNotEmpty()) e.smsTime else UiUtil.dateTime(e.createdAt)

            val err = row.findViewById<TextView>(R.id.tvError)
            if (e.error.isNullOrEmpty()) {
                err.visibility = View.GONE
            } else {
                err.visibility = View.VISIBLE
                err.text = e.error
            }
            list.addView(row)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Manual send                                                         */
/* ------------------------------------------------------------------ */
class ManualFragment : Fragment(R.layout.fragment_manual) {

    private fun selectedProvider(v: View): Provider =
        when (v.findViewById<RadioGroup>(R.id.rgProvider).checkedRadioButtonId) {
            R.id.rbNagad -> Providers.NAGAD
            R.id.rbRocket -> Providers.ROCKET
            else -> Providers.BKASH
        }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val etSms = v.findViewById<EditText>(R.id.etSms)

        v.findViewById<View>(R.id.btnPaste).setOnClickListener {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
            if (t.isBlank()) toast("Clipboard is empty") else etSms.setText(t)
        }
        v.findViewById<View>(R.id.btnClear).setOnClickListener {
            etSms.setText("")
            v.findViewById<View>(R.id.resultCard).visibility = View.GONE
        }
        v.findViewById<View>(R.id.btnSendManual).setOnClickListener { send(v) }
    }

    private fun showResult(v: View, msg: String, level: Int) {
        val card = v.findViewById<View>(R.id.resultCard)
        val tv = v.findViewById<TextView>(R.id.tvResult)
        card.visibility = View.VISIBLE
        tv.text = msg
        tv.setTextColor(UiUtil.levelColor(requireContext(), level))
    }

    private fun send(v: View) {
        val ctx = requireContext()
        val text = v.findViewById<EditText>(R.id.etSms).text.toString().trim()
        if (text.isEmpty()) {
            showResult(v, "Paste the full SMS first.", 2)
            return
        }
        val provider = selectedProvider(v)
        val parsed = SmsParsers.parse(provider, text)
        if (parsed == null) {
            showResult(
                v,
                "This text does not look like a ${provider.label} \"money received\" SMS. " +
                    "Copy the full original SMS and try again.",
                2
            )
            return
        }

        val btn = v.findViewById<View>(R.id.btnSendManual)
        btn.isEnabled = false
        showResult(v, "Sending...", 1)

        val app = ctx.applicationContext
        val act = requireActivity()
        val online = UiUtil.isOnline(ctx)

        Thread {
            val db = Db.get(app)
            var info: String? = null

            val existing = db.find(parsed.trxId)
            if (existing == null) {
                db.insert(
                    Ev(
                        localId = UUID.randomUUID().toString(),
                        provider = provider.key,
                        trxId = parsed.trxId,
                        amount = parsed.amount,
                        sender = parsed.sender,
                        smsTime = parsed.smsTime,
                        raw = text,
                        status = St.PENDING,
                        error = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
            } else if (existing.status in St.retryable) {
                db.resetToPending(existing.localId, text)
            } else {
                info = "This TrxID (${parsed.trxId}) was already sent: " +
                    UiUtil.statusLabel(existing.status)
            }

            var msg: String
            var level: Int
            if (info != null) {
                msg = info
                level = 0
            } else if (!Prefs(app).isConfigured) {
                msg = "Saved in Pending, but the device is not paired. Open Device Settings."
                level = 2
            } else if (!online) {
                msg = "Internet is OFF. Saved in Pending.\nTurn internet on, then open Pending page and tap \"Send pending now\"."
                level = 1
            } else {
                val out = SyncEngine.run(app)
                val ev = db.find(parsed.trxId)
                when {
                    ev == null -> { msg = "Something went wrong."; level = 2 }
                    ev.status == St.USED -> {
                        msg = "Already used on the website (${ev.trxId})."
                        level = 0
                    }
                    ev.status == St.SYNCED -> {
                        msg = "Sent to server.\n${provider.label}  ${UiUtil.money(ev.amount)}  |  ${ev.trxId}"
                        level = 0
                    }
                    ev.status == St.DUPLICATE -> {
                        msg = "Server already has this TrxID (${ev.trxId})."
                        level = 0
                    }
                    ev.status == St.FLAGGED -> {
                        msg = "Sent, but flagged for admin review."
                        level = 1
                    }
                    ev.status == St.REJECTED -> {
                        msg = "Rejected by server: " + (ev.error ?: "")
                        level = 2
                    }
                    ev.status == St.FAILED -> {
                        msg = "Failed: " + (ev.error ?: "")
                        level = 2
                    }
                    else -> {
                        msg = if (out == SyncEngine.Outcome.RETRY)
                            "Server/network problem. Saved in Pending and will retry."
                        else "Saved in Pending."
                        level = 1
                    }
                }
            }

            act.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                btn.isEnabled = true
                showResult(v, msg, level)
            }
        }.start()
    }
}

/* ------------------------------------------------------------------ */
/* Device settings                                                     */
/* ------------------------------------------------------------------ */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val prefs = Prefs(ctx)

        val etUrl = v.findViewById<EditText>(R.id.etUrl)
        val etToken = v.findViewById<EditText>(R.id.etToken)
        val etSecret = v.findViewById<EditText>(R.id.etSecret)
        etUrl.setText(prefs.url)
        etToken.setText(prefs.token)
        etSecret.setText(prefs.secret)

        v.findViewById<View>(R.id.btnSave).setOnClickListener {
            val url = etUrl.text.toString().trim()
            val token = etToken.text.toString().trim()
            val secret = etSecret.text.toString().trim()
            if (!url.startsWith("https://")) {
                toast("URL must start with https://")
                return@setOnClickListener
            }
            if (token.isEmpty() || secret.isEmpty()) {
                toast("Token and Secret are required")
                return@setOnClickListener
            }
            prefs.url = url
            prefs.token = token
            prefs.secret = secret
            prefs.lastResult = ""
            toast("Saved")
            MonitorService.ensure(ctx)
            SyncRunner.trigger(ctx)
        }

        v.findViewById<View>(R.id.btnTestConn).setOnClickListener {
            toast("Testing connection...")
            val app = ctx.applicationContext
            val a = requireActivity()
            Thread {
                val msg = SyncEngine.ping(app)
                a.runOnUiThread { Toast.makeText(a, msg, Toast.LENGTH_LONG).show() }
            }.start()
        }

        v.findViewById<View>(R.id.btnDisconnect).setOnClickListener {
            prefs.clear()
            etUrl.setText("")
            etToken.setText("")
            etSecret.setText("")
            toast("Device disconnected")
        }

        val switches = mapOf(
            Providers.BKASH to v.findViewById<SwitchMaterial>(R.id.swBkash),
            Providers.NAGAD to v.findViewById<SwitchMaterial>(R.id.swNagad),
            Providers.ROCKET to v.findViewById<SwitchMaterial>(R.id.swRocket)
        )
        for ((p, sw) in switches) {
            sw.isChecked = prefs.providerEnabled(p)
            sw.setOnCheckedChangeListener { _, checked -> prefs.setProviderEnabled(p, checked) }
        }
    }
}

/* ------------------------------------------------------------------ */
/* About                                                               */
/* ------------------------------------------------------------------ */
class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val ver = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        v.findViewById<TextView>(R.id.tvVersion).text = "Version $ver"
        v.findViewById<View>(R.id.btnWebsite).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_url))))
            } catch (e: Exception) {
                toast("No browser found")
            }
        }
    }
}
