package com.emrobinso.floatpic.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Client for the public Gradio Space
 * `prithivmlmods/Qwen-Image-Edit-2511-LoRAs-Fast`.
 *
 * Guest mode: no token. The space may be asleep, which shows up as a slow first request or
 * an HTTP 503 — both are surfaced as a readable message instead of a silent failure.
 *
 * Flow (from the space's agents.md + app.py):
 *   POST /gradio_api/call/v2/edit_image  {images_b64_json, prompt, lora_adapter, seed,
 *                                        randomize_seed, guidance_scale, steps}
 *   -> {"event_id": "..."}
 *   GET  /gradio_api/call/edit_image/{event_id}   (SSE lines: "data: {...}")
 */
object AiEditClient {

    private const val BASE = "https://prithivmlmods-qwen-image-edit-2511-loras-fast.hf.space"
    private const val CALL = "$BASE/gradio_api/call/v2/edit_image"
    private const val POLL = "$BASE/gradio_api/call/edit_image"

    /** LoRA ids exposed by the space (used as "gaya" in the panel). */
    val LORAS = listOf(
        "Photo-to-Anime", "Anime-V2", "Upscaler", "Unblur-Anything", "Polaroid-Photo",
        "Studio-DeLight", "Any-light", "Light-Migration", "Style-Transfer",
        "Ultra-Realistic-Portrait", "Multiple-Angles",
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val seedCounter = AtomicInteger(1)

    sealed class Result {
        data class Ok(val bitmap: Bitmap) : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * Runs one edit. [correction] is appended as a "hint" so a re-run after a bad result
     * says what was wrong ("too dark", "keep the face") instead of re-rolling blindly.
     */
    suspend fun edit(
        source: Bitmap,
        prompt: String,
        lora: String?,
        correction: String? = null,
        onStage: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        runCatching {
            val effective = buildString {
                append(prompt.trim())
                if (!correction.isNullOrBlank()) {
                    append(". Important: ").append(correction.trim())
                }
            }
            if (effective.isBlank()) return@runCatching Result.Failed("prompt kosong")

            onStage("upload")
            val b64 = encode(source)
            val payload = buildJsonObject {
                put("images_b64_json", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(b64)) }.toString())
                put("prompt", effective)
                // the space always needs an adapter id (it has no "None"), so a plain
                // prompt run uses the least stylising one available
                put("lora_adapter", lora ?: "Unblur-Anything")
                put("seed", seedCounter.incrementAndGet())
                put("randomize_seed", true)
                put("guidance_scale", 4.0)
                put("steps", 4)
            }.toString()

            onStage("generate")
            val eventId = postJson(CALL, payload)
                ?: return@runCatching Result.Failed("server tidak mengembalikan event id")

            val resultJson = poll(POLL + "/" + eventId)
                ?: return@runCatching Result.Failed("tidak ada balasan dari server")

            // payload: {"image": "data:image/png;base64,...", "status": "success", ...}
            val status = resultJson["status"]?.jsonPrimitive?.safeContent()
            if (status != null && status != "success") {
                val msg = resultJson["message"]?.jsonPrimitive?.safeContent()
                return@runCatching Result.Failed(msg ?: status)
            }
            val dataUrl = resultJson["image"]?.jsonPrimitive?.safeContent()
                ?: return@runCatching Result.Failed("hasil kosong")
            val raw = dataUrl.substringAfter(',', dataUrl)
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@runCatching Result.Failed("gambar hasil tidak valid")
            Result.Ok(bmp)
        }.getOrElse { e ->
            Result.Failed(friendly(e))
        }
    }

    /** Saves a result next to the session so it can replace the source later. */
    fun persist(context: android.content.Context, bitmap: Bitmap): File? = runCatching {
        val dir = File(context.filesDir, "ai").apply { mkdirs() }
        prune(dir)
        val out = File(dir, "ai_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        out.takeIf { it.length() > 0L }
    }.getOrNull()

    // -- transport ---------------------------------------------------------------------

    private fun encode(bitmap: Bitmap): String {
        // the space resizes to <=1024 on the long side; sending more only wastes upload time
        val long = max(bitmap.width, bitmap.height)
        val scale = if (long > 1024) 1024f / long else 1f
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(8),
                (bitmap.height * scale).toInt().coerceAtLeast(8),
                true,
            )
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, baos)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun postJson(url: String, body: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code ${text.take(160)}")
            }
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: return null
            obj["event_id"]?.jsonPrimitive?.safeContent()
        } finally {
            conn.disconnect()
        }
    }

    private fun poll(url: String): JsonObject? {
        val deadline = System.currentTimeMillis() + 6 * 60 * 1000
        while (System.currentTimeMillis() < deadline) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("Accept", "text/event-stream")
            }
            var last: JsonObject? = null
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IOException("HTTP $code saat menunggu hasil")
                }
                conn.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (!line.startsWith("data:")) return@forEach
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) return@forEach
                        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
                        // Gradio streams progress messages too; keep the last full object
                        if (obj != null) last = obj
                    }
                }
            } finally {
                conn.disconnect()
            }
            val done = last
            if (done != null && (done["image"] != null || done["status"] != null)) return done
            kotlinx.coroutines.delay(3_000)
        }
        return null
    }


    private fun friendly(t: Throwable): String {
        val raw = t.message ?: t::class.java.simpleName
        return when {
            raw.contains("503") -> "server AI sedang tidur, coba lagi sebentar"
            raw.contains("429") -> "kuota server AI penuh, coba lagi nanti"
            raw.contains("timeout", ignoreCase = true) -> "koneksi ke server AI timeout"
            raw.contains("Unable to resolve host", ignoreCase = true) -> "tidak ada internet"
            else -> raw.take(160)
        }
    }

    /** JsonPrimitive.content throws for null/objects; keep it defensive. */
    private fun JsonPrimitive.safeContent(): String? = runCatching { content }.getOrNull()

    private fun prune(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(12).forEach { runCatching { it.delete() } }
    }
}
