package com.emrobinso.floatpic.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks a public echo service for this device's public IP.
 *
 * Used only to label the local AI quota counter: "IP 103.28.x.x — 2/3 hari ini". Nothing is
 * stored remotely and the result is cached for an hour: the quota is per IP per day, so a
 * fresh lookup on every run would be both pointless and rude to the echo service.
 */
object IpInfo {

    private var cachedIp: String? = null
    private var cachedAt: Long = 0L
    private const val TTL_MS = 60 * 60 * 1000L

    /**
     * Blocking; call from a background dispatcher. Returns null when offline.
     *
     * [refresh] forces a new lookup — needed when the user switched network/IP on purpose,
     * where a cached answer would show the old address and the old allowance.
     */
    fun lookup(refresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        val hit = cachedIp
        if (!refresh && hit != null && now - cachedAt < TTL_MS) return hit

        // two independent services, whichever answers first with something IP-shaped
        val endpoints = listOf(
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
        )
        for (url in endpoints) {
            val value = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 6_000
                }
                try {
                    if (conn.responseCode !in 200..299) return@runCatching null
                    conn.inputStream.bufferedReader().use { it.readText() }.trim()
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            if (value != null && looksLikeIp(value)) {
                cachedIp = value
                cachedAt = now
                return value
            }
        }
        return hit
    }

    private fun looksLikeIp(raw: String): Boolean {
        val parts = raw.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }
}
