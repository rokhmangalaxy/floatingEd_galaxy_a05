package com.emrobinso.floatpic.data

import android.content.Context
import java.io.File

/**
 * Small on-disk crash journal.
 *
 * The previous build died on the very first frame and all the phone showed was
 * "aplikasi memiliki bug". Anything that can throw during startup now writes a short,
 * human-readable line here, and the next launch shows it as a toast + keeps the file for
 * `adb shell run-as com.emrobinso.floatpic cat files/last_crash.txt`.
 */
object CrashLog {

    private const val FILE = "last_crash.txt"

    fun file(context: Context) = File(context.filesDir, FILE)

    fun write(context: Context, error: Throwable) = runCatching {
        val trace = error.stackTraceToString().take(4000)
        file(context).writeText(
            "time=${System.currentTimeMillis()}\n${error::class.java.name}: ${error.message}\n\n$trace",
        )
    }

    /** Returns and clears the previous crash, if any. */
    fun take(context: Context): String? = runCatching {
        val f = file(context)
        if (!f.exists() || f.length() == 0L) return null
        val text = f.readText()
        f.delete()
        text
    }.getOrNull()

    /** One line summary, for a toast. */
    fun summary(raw: String): String {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        val kind = lines.getOrNull(1) ?: lines.firstOrNull() ?: "unknown"
        return kind.take(140)
    }
}
