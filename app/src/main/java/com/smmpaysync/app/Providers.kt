package com.smmpaysync.app

data class Provider(
    val key: String,
    val label: String,
    val smsHeader: String,     // sender name/number shown by the phone
    val serverSender: String,  // value sent in "sender" field to the server
    val color: Int
)

object Providers {
    val BKASH = Provider("bkash", "bKash", "bKash", "bKash", 0xFFE2136E.toInt())
    val NAGAD = Provider("nagad", "Nagad", "NAGAD", "Nagad", 0xFFF6921E.toInt())
    val ROCKET = Provider("rocket", "Rocket", "16216", "Rocket", 0xFF8C1D82.toInt())

    val all = listOf(BKASH, NAGAD, ROCKET)

    fun byKey(k: String?): Provider = all.firstOrNull { it.key == k } ?: BKASH

    fun byHeader(h: String): Provider? =
        all.firstOrNull { it.smsHeader.equals(h.trim(), ignoreCase = true) }
}
