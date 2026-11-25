package com.emrobinso.floatpic.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
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
    private const val KEY_BACKEND = "backend"
    private const val KEY_PINNED = "pinned"
    private const val KEY_USES = "uses"

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())
    private val intMap = MapSerializer(String.serializer(), Int.serializer())

    fun load(context: Context): List<String> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        json.decodeFromString(listSerializer, raw).filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Drops one saved prompt. */
    fun remove(context: Context, text: String): List<String> {
        val next = load(context).filter { it != text }
        write(context, next)
        return next
    }

    /** Drops every saved prompt. */
    fun clear(context: Context): List<String> {
        write(context, emptyList())
        return emptyList()
    }

    private fun write(context: Context, list: List<String>) {
        runCatching {
            val raw = json.encodeToString(listSerializer, list)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, raw).apply()
        }
    }

    /** Renames a prompt in place; the position (and so its rank) is kept. */
    fun rename(context: Context, from: String, to: String): List<String> {
        val clean = to.trim()
        if (clean.isBlank()) return load(context)
        val current = load(context)
        val next = current.map { if (it == from) clean else it }.distinct()
        write(context, next)
        // carry the metadata along, otherwise a renamed prompt loses its star and count
        runCatching {
            val pins = pinned(context)
            if (from in pins) setPinned(context, clean, true)
            val counts = uses(context)
            counts[from]?.let { wrote(context, clean, it) }
        }
        return next
    }

    // ---- stars -------------------------------------------------------------------
    fun pinned(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_PINNED, null) ?: return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    fun setPinned(context: Context, text: String, on: Boolean) {
        val next = pinned(context).filter { it != text }.toMutableList()
        if (on) next.add(0, text)
        runCatching {
            prefs(context).edit().putString(KEY_PINNED, json.encodeToString(listSerializer, next)).apply()
        }
    }

    // ---- use counters ------------------------------------------------------------
    fun uses(context: Context): MutableMap<String, Int> {
        val raw = prefs(context).getString(KEY_USES, null) ?: return mutableMapOf()
        return runCatching { json.decodeFromString(intMap, raw).toMutableMap() }
            .getOrDefault(mutableMapOf())
    }

    fun wrote(context: Context, text: String, count: Int) {
        val next = uses(context)
        next[text] = count
        saveUses(context, next)
    }

    fun bump(context: Context, text: String) {
        val next = uses(context)
        next[text] = (next[text] ?: 0) + 1
        saveUses(context, next)
    }

    private fun saveUses(context: Context, map: Map<String, Int>) {
        runCatching {
            prefs(context).edit().putString(KEY_USES, json.encodeToString(intMap, map)).apply()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Which AI server was picked last (its name, see [AiBackend]). */
    fun backend(context: Context): String? = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BACKEND, null)
    }.getOrNull()

    fun setBackend(context: Context, name: String) = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKEND, name).apply()
    }.let { }

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
