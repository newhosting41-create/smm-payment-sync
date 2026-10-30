package com.smmpaysync.app

data class ParsedSms(
    val provider: Provider,
    val amount: Double,
    val sender: String,
    val fee: Double,
    val balance: Double,
    val trxId: String,
    val smsTime: String
)

/**
 * bKash: strict pattern (same as the server).
 * Nagad / Rocket: lenient keyword based parser (amount + TrxID must exist,
 * message must be a "received" message). Check these with real SMS samples.
 */
object SmsParsers {

    private val spaces = Regex("\\s+")

    private val bkash = Regex(
        """^You have received Tk\s*([\d,]+(?:\.\d{2})?)\s+from\s+(01\d{9})\.?""" +
            """(?:\s+Ref\s+[^\r\n]*?\.)?""" +
            """\s+(?:Fee|Commission)\s+Tk\s*([\d,]+(?:\.\d{2})?)\.""" +
            """\s+Balance\s+Tk\s*([\d,]+(?:\.\d{2})?)\.""" +
            """\s+TrxID\s+([A-Z0-9]{8,15})\s+at\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})"""
    )

    private val ci = RegexOption.IGNORE_CASE
    private val amountRe = Regex("""(?:Amount\s*:?|received)\s*(?:Tk\.?|BDT)\s*([\d,]+(?:\.\d+)?)""", ci)
    private val trxRe = Regex("""(?:Txn\s*ID|Trx\s*ID|Transaction\s*ID)\s*:?\s*([A-Za-z0-9]{6,20})""", ci)
    private val senderRe = Regex("""(?:Sender|from)\s*:?\s*(01\d{9})""", ci)
    private val feeRe = Regex("""Fee\s*:?\s*Tk\.?\s*([\d,]+(?:\.\d+)?)""", ci)
    private val balRe = Regex("""Balance\s*:?\s*Tk\.?\s*([\d,]+(?:\.\d+)?)""", ci)
    private val timeRe = Regex("""(\d{2}[/-]\d{2}[/-]\d{2,4}\s+\d{2}:\d{2})""")

    private fun num(re: Regex, s: String): Double =
        re.find(s)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    fun parse(provider: Provider, body: String): ParsedSms? {
        val clean = body.trim().replace(spaces, " ")
        return if (provider.key == Providers.BKASH.key) parseBkash(clean) else parseLenient(provider, clean)
    }

    private fun parseBkash(clean: String): ParsedSms? {
        val m = bkash.find(clean) ?: return parseLenient(Providers.BKASH, clean)
        val g = m.groupValues
        return try {
            ParsedSms(
                Providers.BKASH,
                g[1].replace(",", "").toDouble(),
                g[2],
                g[3].replace(",", "").toDouble(),
                g[4].replace(",", "").toDouble(),
                g[5].uppercase(),
                g[6]
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun parseLenient(provider: Provider, clean: String): ParsedSms? {
        if (!clean.contains("receiv", ignoreCase = true)) return null
        val amount = num(amountRe, clean)
        if (amount <= 0.0) return null
        val trx = trxRe.find(clean)?.groupValues?.get(1)?.uppercase() ?: return null
        return ParsedSms(
            provider,
            amount,
            senderRe.find(clean)?.groupValues?.get(1) ?: "",
            num(feeRe, clean),
            num(balRe, clean),
            trx,
            timeRe.find(clean)?.groupValues?.get(1) ?: ""
        )
    }
}
