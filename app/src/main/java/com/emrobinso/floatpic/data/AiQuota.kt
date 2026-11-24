package com.emrobinso.floatpic.data

import android.content.Context

/**
 * Counts successful AI generations for this device's IP.
 *
 * The public Spaces give an anonymous visitor a small daily allowance — the Rapid-AIO space
 * is 3 runs per IP per 24 h. The count is local (we cannot read the server's ledger), so it
 * is a *forecast*, not a gate: the app still lets the user try, it just says "this is run 2
 * of 3 today" so a refusal from the server is no longer a surprise.
 *
 * The counter resets 24 h after the first counted run.
 */
object AiQuota {

    const val LIMIT = 3
    private const val PREFS = "floatpic_ai_quota"
    private const val KEY_IP = "ip"
    private const val KEY_COUNT = "count"
    private const val KEY_SINCE = "since"
    private const val KEY_LAST = "last_at"

    data class State(
        val ip: String?,
        val used: Int,
        val since: Long,
        val lastAt: Long,
    ) {
        val remaining: Int get() = (LIMIT - used).coerceAtLeast(0)
        val windowLeftMs: Long
            get() = (since + 24 * 60 * 60 * 1000L - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun state(context: Context): State {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = State(
            ip = p.getString(KEY_IP, null),
            used = p.getInt(KEY_COUNT, 0),
            since = p.getLong(KEY_SINCE, 0L),
            lastAt = p.getLong(KEY_LAST, 0L),
        )
        // expired window: pretend nothing was used
        if (s.since > 0L && s.windowLeftMs == 0L) return s.copy(used = 0)
        return s
    }

    /** Records one finished run for [ip] (starting a new window when the old one expired). */
    fun record(context: Context, ip: String?) {
        val now = System.currentTimeMillis()
        val current = state(context)
        val sameIp = current.ip == null || current.ip == ip
        val expired = current.since == 0L || current.windowLeftMs == 0L
        val used = if (!sameIp || expired) 1 else current.used + 1
        val since = if (!sameIp || expired) now else current.since
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_IP, ip)
            .putInt(KEY_COUNT, used)
            .putLong(KEY_SINCE, since)
            .putLong(KEY_LAST, now)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
