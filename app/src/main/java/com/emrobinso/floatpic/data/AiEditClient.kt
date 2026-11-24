package com.emrobinso.floatpic.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 * The space is a `gradio.Server` (not Blocks), and the *only* documented way its own web UI
 * talks to `edit_image` is through the Gradio JS client. That client does:
 *
 *   1. `POST /gradio_api/call/edit_image`
 *      body: `{"data": [images_b64_json, prompt, lora_adapter, seed, randomize_seed,
 *                       guidance_scale, steps], "session_hash": "..."}`
 *      -> `{"event_id": "..."}`
 *   2. `GET /gradio_api/call/edit_image/{event_id}`  (SSE)
 *      `event: generating` + `data: {...}`      progress / queue position
 *      `event: complete`   + `data: [ {...} ]`  the return value, a list with one dict
 *      `event: error`      + `data: {...}`      failure
 *
 * The earlier version posted a *named object* to a `.../call/v2/...` endpoint. The space
 * answered, but nothing ever arrived as a `complete` event, so the UI span forever — which
 * is exactly the bug reported. Everything below follows the JS client now.
 */
object AiEditClient {

    private const val BASE = "https://prithivmlmods-qwen-image-edit-2511-loras-fast.hf.space"
    private const val CALL = "$BASE/gradio_api/call/edit_image"

    /** LoRA ids exposed by the space (used as "gaya" in the panel). */
    val LORAS = listOf(
        "Photo-to-Anime", "Anime-V2", "Upscaler", "Unblur-Anything", "Polaroid-Photo",
        "Studio-DeLight", "Any-light", "Light-Migration", "Style-Transfer",
        "Ultra-Realistic-Portrait", "Multiple-Angles",
    )

    /** Adapter id used when the user picks "Tanpa gaya" (the space has no "None"). */
    const val DEFAULT_LORA = "Unblur-Anything"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val seedCounter = AtomicInteger(1)

    sealed class Result {
        data class Ok(val bitmap: Bitmap) : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * Runs one edit.
     *
     * @param onStage human-readable progress ("antrean 3", "membuat 40%"), shown next to
     *                the timer so a long queue never looks frozen
     * @param onLog   every transport step, so the user can copy a readable trace
     */
    suspend fun edit(
        source: Bitmap,
        prompt: String,
        lora: String?,
        correction: String? = null,
        onStage: (String) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        runCatching {
            val effective = buildString {
                append(prompt.trim())
                if (!correction.isNullOrBlank()) {
                    append(". Important: ").append(correction.trim())
                }
            }
            if (effective.isBlank()) return@runCatching Result.Failed("prompt kosong")

            onStage("menyiapkan gambar")
            onLog("POST $CALL")
            val b64 = encode(source)
            onLog("gambar ${source.width}x${source.height} -> ${b64.length / 1024} KB base64")

            // NOTE: positional array. This is what the Gradio client sends, and the order
            // must match the python signature exactly.
            val payload = buildJsonObject {
                put(
                    "data",
                    buildJsonArray {
                        add(JsonPrimitive(buildJsonArray { add(JsonPrimitive(b64)) }.toString()))
                        add(JsonPrimitive(effective))
                        add(JsonPrimitive(lora ?: DEFAULT_LORA))
                        add(JsonPrimitive(seedCounter.incrementAndGet()))
                        add(JsonPrimitive(true))
                        add(JsonPrimitive(4.0))
                        add(JsonPrimitive(4))
                    },
                )
                put("session_hash", java.util.UUID.randomUUID().toString().replace("-", "").take(11))
            }.toString()

            val eventId = postJson(CALL, payload, onLog)
                ?: return@runCatching Result.Failed("server tidak mengembalikan event id")
            onLog("event_id=$eventId")

            onStage("menunggu antrean")
            val outcome = poll("$CALL/$eventId", onStage, onLog)

            when (outcome) {
                is Poll.Done -> {
                    val data = outcome.value
                    val status = data["status"]?.jsonPrimitive?.safeContent()
                    if (status != null && status != "success") {
                        val msg = data["message"]?.jsonPrimitive?.safeContent()
                        onLog("status=$status ${msg.orEmpty()}")
                        return@runCatching Result.Failed(msg ?: status)
                    }
                    val dataUrl = data["image"]?.jsonPrimitive?.safeContent()
                        ?: return@runCatching Result.Failed("hasil kosong")
                    onLog("gambar hasil: ${dataUrl.take(40)}… (${dataUrl.length / 1024} KB)")
                    val raw = dataUrl.substringAfter(',', dataUrl)
                    val bytes = Base64.decode(raw, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@runCatching Result.Failed("gambar hasil tidak valid")
                    onLog("selesai ${bmp.width}x${bmp.height}")
                    Result.Ok(bmp)
                }
                is Poll.Error -> Result.Failed(outcome.message)
                Poll.Timeout -> Result.Failed("server tidak selesai dalam 5 menit")
            }
        }.getOrElse { e ->
            onLog("exception: ${e::class.java.simpleName}: ${e.message}")
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

    private fun postJson(url: String, body: String, onLog: (String) -> Unit): String? {
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
            onLog("HTTP $code ${text.take(180).replace('\n', ' ')}")
            if (code !in 200..299) throw IOException("HTTP $code ${text.take(160)}")

            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val fromObject = obj?.get("event_id")?.jsonPrimitive?.safeContent()
            obj?.get("error")?.jsonPrimitive?.safeContent()?.let { throw IOException(it) }
            fromObject ?: runCatching { json.parseToJsonElement(text).jsonPrimitive.content }.getOrNull()
        } finally {
            conn.disconnect()
        }
    }

    private sealed class Poll {
        data class Done(val value: JsonObject) : Poll()
        data class Error(val message: String) : Poll()
        data object Timeout : Poll()
    }

    /**
     * Reads the SSE stream the way gradio_client does: `event:` names the frame type,
     * `data:` carries its JSON, a blank line closes the frame. Older Gradio builds omit the
     * `event:` line, so the payload is also inspected directly.
     */
    private suspend fun poll(
        url: String,
        onStage: (String) -> Unit,
        onLog: (String) -> Unit,
    ): Poll {
        val deadline = System.currentTimeMillis() + 5 * 60 * 1000
        onLog("GET $url")
        while (System.currentTimeMillis() < deadline) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("Accept", "text/event-stream")
            }
            var event = ""
            val dataLines = mutableListOf<String>()
            try {
                val code = conn.responseCode
                onLog("SSE HTTP $code")
                if (code !in 200..299) throw IOException("HTTP $code saat menunggu hasil")

                fun frame(): Poll? {
                    if (dataLines.isEmpty()) {
                        event = ""
                        return null
                    }
                    val raw = dataLines.joinToString("\n").trim()
                    dataLines.clear()
                    val type = event.ifBlank { "" }.trim()
                    event = ""
                    if (raw.isEmpty() || raw == "null") return null
                    onLog("event=$type ${raw.take(120).replace('\n', ' ')}")

                    val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
                    // data is the positional list of return values: [{"image": ...}]
                    val obj = when (parsed) {
                        is JsonArray -> parsed.firstOrNull() as? JsonObject
                        is JsonObject -> parsed
                        else -> null
                    }
                    when (type) {
                        "complete" -> if (obj != null) return Poll.Done(obj)
                        "error" -> {
                            val msg = (obj?.get("error") ?: obj?.get("message"))
                                ?.jsonPrimitive?.safeContent()
                            return Poll.Error(msg ?: "server mengembalikan error")
                        }
                        "generating", "status" -> {
                            val stage = progressText(type, obj ?: parsed as? JsonObject)
                            if (stage != null) onStage(stage)
                        }
                    }
                    // fallback for builds without the `event:` line
                    if (obj != null && (obj["image"] != null || obj["status"] != null)) {
                        return Poll.Done(obj)
                    }
                    return null
                }

                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.isEmpty()) {
                            val done = frame()
                            if (done != null) return done
                        } else if (line.startsWith("event:")) {
                            event = line.removePrefix("event:")
                        } else if (line.startsWith("data:")) {
                            dataLines.add(line.removePrefix("data:").trim())
                        }
                    }
                    // stream closed: flush whatever is left
                    frame()?.let { return it }
                }
            } catch (e: IOException) {
                onLog("SSE error: ${e.message}")
                throw e
            } finally {
                conn.disconnect()
            }
            delay(2_000)
        }
        onLog("timeout setelah 5 menit")
        return Poll.Timeout
    }

    /** Turns gradio's status frames into something a human can read. */
    private fun progressText(type: String, obj: JsonObject?): String? {
        if (obj == null) return null
        val status = obj["status"]?.jsonPrimitive?.safeContent()
        val queue = obj["rank"]?.jsonPrimitive?.safeContent()
            ?: obj["position"]?.jsonPrimitive?.safeContent()
        if (status == "pending") {
            return if (queue != null) "antrean #${queue.toIntOrNull()?.plus(1) ?: queue}" else "antrean"
        }
        val pd = runCatching { obj["progress_data"] as? JsonArray }.getOrNull()
        val last = pd?.lastOrNull() as? JsonObject
        val index = last?.get("index")?.jsonPrimitive?.safeContent()?.toFloatOrNull()
        val length = last?.get("length")?.jsonPrimitive?.safeContent()?.toFloatOrNull()
        if (index != null && length != null && length > 0f) {
            return "membuat ${(index / length * 100).toInt()}%"
        }
        return when (type) {
            "generating" -> "membuat gambar"
            else -> null
        }
    }

    private fun JsonPrimitive.safeContent(): String? = runCatching { content }.getOrNull()

    private fun friendly(t: Throwable): String {
        val raw = t.message ?: t::class.java.simpleName
        return when {
            raw.contains("503") -> "server AI sedang tidur, coba lagi sebentar"
            raw.contains("429") -> "kuota server AI penuh, coba lagi nanti"
            raw.contains("timeout", ignoreCase = true) -> "koneksi ke server AI timeout"
            raw.contains("Unable to resolve host") || raw.contains("UnknownHost") ->
                "tidak ada internet"
            raw.contains("HTTP 4") -> "server menolak permintaan ($raw)"
            else -> raw.take(160)
        }
    }

    private fun prune(dir: File) {
        runCatching {
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(12)?.forEach { it.delete() }
        }
    }
}
