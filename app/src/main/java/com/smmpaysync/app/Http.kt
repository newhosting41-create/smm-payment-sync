package com.smmpaysync.app

import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Http {

    fun hmacHex(secret: String, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    /**
     * The signature is calculated over the exact bytes that are sent,
     * and the server verifies HMAC over the raw request body.
     */
    fun post(url: String, token: String, secret: String, body: String): Pair<Int, String> {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.doOutput = true
            c.instanceFollowRedirects = false
            c.setFixedLengthStreamingMode(bytes.size)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("X-Device-Token", token)
            c.setRequestProperty("X-Signature", hmacHex(secret, bytes))
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Pair(code, text)
        } finally {
            c.disconnect()
        }
    }
}
