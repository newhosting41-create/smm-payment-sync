package com.smmpaysync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs == null || msgs.isEmpty()) return

        val app = context.applicationContext
        val from = msgs[0].originatingAddress ?: ""
        val provider = Providers.byHeader(from)
        if (provider == null) {
            Prefs(app).note("SMS from '$from' ignored (not a bKash sender)")
            return
        }
        val body = msgs.joinToString("") { it.messageBody ?: "" }

        val pending = goAsync()
        Thread {
            try {
                val prefs = Prefs(app)
                if (!prefs.providerEnabled(provider)) {
                    prefs.note("bKash SMS seen, but bKash switch is OFF")
                    return@Thread
                }

                val p = SmsParsers.parse(provider, body)
                if (p == null) {
                    if (body.contains("TrxID", ignoreCase = true)) {
                        // Payment-looking SMS we could not read: keep it visible in Pending.
                        Db.get(app).insert(
                            Ev(
                                localId = UUID.randomUUID().toString(),
                                provider = provider.key,
                                trxId = "UNREAD-" + UUID.randomUUID().toString(),
                                amount = 0.0,
                                sender = "",
                                smsTime = "",
                                raw = body,
                                status = St.FAILED,
                                error = "Could not read this SMS. Copy it and use Send SMS Manually.",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        prefs.note("payment SMS could not be read, kept in Pending")
                        Notifier.update(app)
                    } else {
                        prefs.note("bKash SMS ignored (not a payment message)")
                    }
                    return@Thread
                }

                Db.get(app).insert(
                    Ev(
                        localId = UUID.randomUUID().toString(),
                        provider = provider.key,
                        trxId = p.trxId,
                        amount = p.amount,
                        sender = p.sender,
                        smsTime = p.smsTime,
                        raw = body,
                        status = St.PENDING,
                        error = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
                prefs.note("payment SMS saved, TrxID ${p.trxId}")
                Notifier.update(app)
                SyncRunner.trigger(app)      // send right now
                SyncWorker.enqueue(app)      // backup
                MonitorService.ensure(app)
            } catch (e: Exception) {
                Prefs(app).note("error while saving SMS: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }
}
