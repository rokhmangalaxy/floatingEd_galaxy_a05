package com.emrobinso.floatpic

import android.app.Application
import android.os.Build
import com.emrobinso.floatpic.data.CrashLog
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Records uncaught crashes to `filesDir/last_crash.txt` before the process dies.
 *
 * A crash in a release build cannot be debugged on the device, and the system only shows
 * "aplikasi memiliki bug". With this file the next launch can tell what actually broke
 * (and `adb shell run-as com.emrobinso.floatpic cat files/last_crash.txt` works too).
 */
class FloatPicApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { sw -> PrintWriter(sw).use { error.printStackTrace(it) } }
                val header = buildString {
                    append("time=").append(System.currentTimeMillis()).append('\n')
                    append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    append("sdk=").append(Build.VERSION.SDK_INT).append('\n')
                    append("app=").append(BuildConfig.VERSION_NAME)
                        .append(" (").append(BuildConfig.VERSION_CODE).append(")\n\n")
                }
                CrashLog.file(this).writeText(header + trace.toString())
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
