package com.emrobinso.floatpic.data

import android.content.Context
import com.emrobinso.floatpic.feature.editor.DraftSnapshot
import com.emrobinso.floatpic.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Tiny JSON-on-disk session store (no Room needed yet, survives process death). */
object DraftStore {

    private fun sessionFile(context: Context, id: String): File =
        File(File(context.filesDir, "drafts").apply { mkdirs() }, "$id.json")

    private fun prefs(context: Context) =
        context.getSharedPreferences("floatpic", Context.MODE_PRIVATE)

    suspend fun save(context: Context, id: String, snapshot: DraftSnapshot) =
        withContext(Dispatchers.IO) {
            sessionFile(context, id).writeText(DraftSnapshot.encode(snapshot))
            prefs(context).edit().putString(ACTIVE_SESSION, id).apply()
        }

    suspend fun load(context: Context, id: String): DraftSnapshot? = withContext(Dispatchers.IO) {
        val f = sessionFile(context, id)
        if (!f.exists()) null else DraftSnapshot.decode(f.readText())
    }

    fun activeSession(context: Context): String? = prefs(context).getString(ACTIVE_SESSION, null)

    suspend fun clear(context: Context, id: String?) = withContext(Dispatchers.IO) {
        id?.let { runCatching { sessionFile(context, it).delete() } }
        prefs(context).edit().remove(ACTIVE_SESSION).apply()
        runCatching {
            File(context.cacheDir, "session").deleteRecursively()
        }
    }

    fun theme(context: Context): AppTheme {
        val raw = prefs(context).getString(KEY_THEME, null) ?: return AppTheme.MAROON_DARK
        return runCatching { AppTheme.valueOf(raw) }.getOrDefault(AppTheme.MAROON_DARK)
    }

    fun setTheme(context: Context, theme: AppTheme) =
        prefs(context).edit().putString(KEY_THEME, theme.name).apply()

    private const val ACTIVE_SESSION = "active_session"
    private const val KEY_THEME = "theme"
}
