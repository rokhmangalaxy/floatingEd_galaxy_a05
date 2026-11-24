package com.emrobinso.floatpic.data

import android.content.Context

/**
 * Keeps track of the per-IP AI allowance **from what the server actually says**.
 *
 * The previous version guessed: it counted successful runs locally and assumed a fixed
 * "3 per 24 h". That was wrong for the way the counter is really used — the user switches to
 * a new IP (VPN / hotspot) *after* the old one is exhausted, so the number shown has to follow
 * the network, not a local tally.
 *
 * The rules here are the ones the Space enforces (Rapid-AIO: 3 anonymous runs per IP per
 * 24 h). The count is keyed by public IP, so a new IP starts at 0 by itself, and it also
 * takes the server's own refusal into account:
 *
 *  - a finished run                      -> used + 1 for that IP
 *  - "you have exceeded …" / 429         -> that IP is marked exhausted right away
 *
 * Nothing is uploaded; the record lives in SharedPreferences.
 */
object AiQuota {

    const val LIMIT = 3
    private const val WINDOW_MS = 24 * 60 * 60 * 1000L
    private const val PREFS = "floatpic_ai_quota_v2"

    data class Usage(val used: Int, val since: Long, val exhausted: Boolean) {
        val windowLeftMs: Long
            get() = (since + WINDOW_MS - System.currentTimeMillis()).coerceAtLeast(0L)
        val remaining: Int get() = if (exhausted) 0 else (LIMIT - used).coerceAtLeast(0)
    }

    /** Current usage for [ip] (an expired window reads as zero). */
    fun usage(context: Context, ip: String?): Usage {
        val key = ip ?: return Usage(0, 0L, false)
        val p = prefs(context)
        val since = p.getLong("${key}_since", 0L)
        if (since == 0L) return Usage(0, 0L, false)
        val expired = since + WINDOW_MS <= System.currentTimeMillis()
        if (expired) {
            p.edit().remove("${key}_used").remove("${key}_since").remove("${key}_exhausted").apply()
            return Usage(0, 0L, false)
        }
        return Usage(
            used = p.getInt("${key}_used", 0),
            since = since,
            exhausted = p.getBoolean("${key}_exhausted", false),
        )
    }

    /** One finished generation for [ip]. */
    fun recordSuccess(context: Context, ip: String?) {
        val key = ip ?: return
        val now = System.currentTimeMillis()
        val current = usage(context, ip)
        val since = if (current.since == 0L) now else current.since
        prefs(context).edit()
            .putInt("${key}_used", current.used + 1)
            .putLong("${key}_since", since)
            .putBoolean("${key}_exhausted", current.used + 1 >= LIMIT)
            .apply()
    }

    /** The server said no (limit reached / 429): this IP is done for the window. */
    fun recordExhausted(context: Context, ip: String?) {
        val key = ip ?: return
        val current = usage(context, ip)
        prefs(context).edit()
            .putBoolean("${key}_exhausted", true)
            .putInt("${key}_used", maxOf(current.used, LIMIT))
            .putLong("${key}_since", if (current.since == 0L) System.currentTimeMillis() else current.since)
            .apply()
    }

    /** Manual reset, kept for the rare case the server forgives earlier than we think. */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when a failure text looks like the per-IP limit rather than a random error. */
    fun looksLikeLimit(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return m.contains("exceed") || m.contains("quota") || m.contains("limit") ||
            m.contains("429") || m.contains("too many")
    }
}
