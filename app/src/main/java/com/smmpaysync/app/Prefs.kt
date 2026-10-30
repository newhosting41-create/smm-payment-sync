package com.smmpaysync.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Prefs(context: Context) {

    private val sp: SharedPreferences = try {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Some low-end phones have broken Keystore; fall back so the app still works.
        context.getSharedPreferences("fallback_prefs", Context.MODE_PRIVATE)
    }

    var url: String
        get() = sp.getString("url", "") ?: ""
        set(v) { sp.edit().putString("url", v).apply() }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) { sp.edit().putString("token", v).apply() }

    var secret: String
        get() = sp.getString("secret", "") ?: ""
        set(v) { sp.edit().putString("secret", v).apply() }

    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) { sp.edit().putLong("last_sync", v).apply() }

    var lastResult: String
        get() = sp.getString("last_result", "") ?: ""
        set(v) { sp.edit().putString("last_result", v).apply() }

    var monitor: Boolean
        get() = sp.getBoolean("monitor", true)
        set(v) { sp.edit().putBoolean("monitor", v).apply() }

    var seenAt: Long
        get() = sp.getLong("seen_at", 0L)
        set(v) { sp.edit().putLong("seen_at", v).apply() }

    var seenInfo: String
        get() = sp.getString("seen_info", "") ?: ""
        set(v) { sp.edit().putString("seen_info", v).apply() }

    /** Records what the SMS receiver did last, so the Dashboard can show where it stopped. */
    fun note(info: String) {
        seenAt = System.currentTimeMillis()
        seenInfo = info
    }

    val isConfigured: Boolean
        get() = url.isNotBlank() && token.isNotBlank() && secret.isNotBlank()

    fun providerEnabled(p: Provider): Boolean =
        sp.getBoolean("prov_" + p.key, p.key == Providers.BKASH.key)

    fun setProviderEnabled(p: Provider, v: Boolean) {
        sp.edit().putBoolean("prov_" + p.key, v).apply()
    }

    /** Disconnect device: remove pairing only, keep provider switches. */
    fun clear() {
        sp.edit()
            .remove("url").remove("token").remove("secret")
            .remove("last_sync").remove("last_result")
            .apply()
    }
}
