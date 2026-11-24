package com.emrobinso.floatpic.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The last [MAX] AI prompts, newest first.
 *
 * Kept in SharedPreferences (a handful of short strings, no need for a file format) and read
 * defensively: a corrupt value just means an empty list, never a crash.
 */
object AiPromptStore {

    const val MAX = 10
    private const val PREFS = "floatpic_ai"
    private const val KEY = "prompts"

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    fun load(context: Context): List<String> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        json.decodeFromString(listSerializer, raw).filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Adds [text] at the front, drops duplicates and keeps the list at [MAX]. */
    fun push(context: Context, text: String): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return load(context)
        val next = (listOf(clean) + load(context).filter { it != clean }).take(MAX)
        runCatching {
            val raw = json.encodeToString(listSerializer, next)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, raw).apply()
        }
        return next
    }
}
