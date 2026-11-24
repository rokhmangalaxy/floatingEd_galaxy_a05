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
import kotlinx.serialization.json.jsonArray
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
 * Talks to whichever [AiBackend] is selected.
 *
 * Protocol (identical for both kinds, this is what `gradio_client` does):
 *
 *   1. `POST {base}/gradio_api/call{endpoint}`  body `{"data": [...], "session_hash": "..."}`
 *      -> `{"event_id": "..."}`
 *   2. `GET  {base}/gradio_api/call{endpoint}/{event_id}`  (SSE)
 *      `event: generating` / `event: complete` / `event: error`, each followed by `data:`.
 *   3. For [AiBackend.Kind.FILE_INPUT] there is an extra first step: upload the JPEG to
 *      `/gradio_api/upload` and pass the returned server path as a Gradio FileData dict.
 *
 * Everything is written out in the log so a failed run can be copied and pasted back here.
 */
object AiEditClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val seedCounter = AtomicInteger(1)

    sealed class Result {
        data class Ok(val bitmap: Bitmap) : Result()
        data class Failed(val message: String) : Result()
    }

    suspend fun edit(
        backend: AiBackend,
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
                if (!correction.isNullOrBlank()) append(". Important: ").append(correction.trim())
            }
            if (effective.isBlank()) return@runCatching Result.Failed("prompt kosong")

            val selector = lora ?: backend.noStyle
            onLog("backend=${backend.label} (${backend.note})")
            onLog("lora=${selector ?: "-"} steps=4")

            val data: JsonArray = when (backend.kind) {
                AiBackend.Kind.FILE_INPUT -> {
                    onStage("mengunggah gambar")
                    val path = upload(backend, source, onLog)
                        ?: return@runCatching Result.Failed("gagal mengunggah gambar ke server")
                    val fileData = buildJsonObject {
                        put("path", path)
                        put("meta", buildJsonObject { put("_type", "gradio.FileData") })
                        put("orig_name", "floatpic.jpg")
                        put("mime_type", "image/jpeg")
                    }
                    // signature of /infer: image1, image2, extras[], prompt, lora, seed,
                    // randomize, cfg, steps, target_mp, extras_cond, pad, vae_tiling,
                    // res_multiple, vae_ref_mp, decoder_vae, keep_2x, hl_protect, hl_strength
                    buildJsonArray {
                        add(fileData)
                        add(JsonPrimitive(""))            // image 2 unused
                        add(buildJsonArray { })           // no extra references
                        add(JsonPrimitive(effective))
                        add(JsonPrimitive(selector ?: "None"))
                        add(JsonPrimitive(seedCounter.incrementAndGet()))
                        add(JsonPrimitive(true))          // randomize_seed
                        add(JsonPrimitive(1.0))           // guidance_scale
                        add(JsonPrimitive(4))             // steps
                        add(JsonPrimitive(1.0))           // target megapixels
                        add(JsonPrimitive(true))          // extras conditioning-only
                        add(JsonPrimitive(true))          // pad to canvas
                        add(JsonPrimitive(false))         // vae tiling
                        add(JsonPrimitive(32))            // resolution multiple
                        add(JsonPrimitive(0.0))           // extra refs VAE mp
                        add(JsonPrimitive("qwen"))        // decoder VAE
                        add(JsonPrimitive(false))         // keep 2x
                        add(JsonPrimitive(true))          // highlight protection
                        add(JsonPrimitive(0.35))          // hl strength
                    }
                }
                AiBackend.Kind.B64_JSON -> {
                    onStage("menyiapkan gambar")
                    val b64 = encodeBase64(source)
                    onLog("gambar ${source.width}x${source.height} -> ${b64.length / 1024} KB base64")
                    buildJsonArray {
                        add(JsonPrimitive(buildJsonArray { add(JsonPrimitive(b64)) }.toString()))
                        add(JsonPrimitive(effective))
                        add(JsonPrimitive(selector ?: "Unblur-Anything"))
                        add(JsonPrimitive(seedCounter.incrementAndGet()))
                        add(JsonPrimitive(true))
                        add(JsonPrimitive(4.0))
                        add(JsonPrimitive(4))
                    }
                }
            }

            val payload = buildJsonObject {
                put("data", data)
                put("session_hash", java.util.UUID.randomUUID().toString().replace("-", "").take(11))
            }.toString()

            onLog("POST ${backend.callUrl}")
            val eventId = postJson(backend.callUrl, payload, onLog)
                ?: return@runCatching Result.Failed("server tidak mengembalikan event id")
            onLog("event_id=$eventId")

            onStage("menunggu antrean")
            val outcome = poll("${backend.callUrl}/$eventId", onStage, onLog)
            when (outcome) {
                is Poll.Done -> decode(outcome.value, backend, onStage, onLog)
                is Poll.Error -> Result.Failed(outcome.message)
                Poll.Timeout -> Result.Failed("server tidak selesai dalam 5 menit")
            }
        }.getOrElse { e ->
            onLog("exception: ${e::class.java.simpleName}: ${e.message}")
            Result.Failed(friendly(e))
        }
    }

    /** Turns one `complete` payload into a bitmap, whichever shape the backend uses. */
    private suspend fun decode(
        value: JsonObject,
        backend: AiBackend,
        onStage: (String) -> Unit,
        onLog: (String) -> Unit,
    ): Result {
        // some backends report a failed run inside a successful event
        val status = value["status"]?.jsonPrimitive?.safeContent()
        if (status != null && status != "success" && status != "ok") {
            val msg = value["message"]?.jsonPrimitive?.safeContent()
            onLog("status=$status ${msg.orEmpty()}")
            return Result.Failed(msg ?: status)
        }

        // shape A: {"image": "data:image/png;base64,..."}   (b64 backends)
        value["image"]?.jsonPrimitive?.safeContent()?.takeIf { it.startsWith("data:") }?.let { url ->
            onLog("gambar base64 ${url.length / 1024} KB")
            return fromBase64(url)
        }
        // shape B: {"url": "https://.../output.png", "path": "..."}   (file backends)
        val remote = value["url"]?.jsonPrimitive?.safeContent()
            ?: value["path"]?.jsonPrimitive?.safeContent()?.takeIf { it.startsWith("http") }
            ?: value["path"]?.jsonPrimitive?.safeContent()?.takeIf { it.isNotBlank() }?.let { p ->
                // a server-local path: reachable through the same host
                if (p.startsWith("/")) backend.base + "/gradio_api/file=" + p else p
            }
        if (remote != null) {
            onLog("unduh ${remote.take(120)}")
            onStage("mengunduh hasil")
            val bytes = getBytes(remote)
                ?: return Result.Failed("gagal mengunduh hasil dari server")
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.Failed("gambar hasil tidak valid")
            return Result.Ok(bmp)
        }
        onLog("payload tak dikenali: ${value.toString().take(200)}")
        return Result.Failed("format hasil tidak dikenali")
    }

    private fun fromBase64(dataUrl: String): Result {
        val raw = dataUrl.substringAfter(',', dataUrl)
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return if (bmp != null) Result.Ok(bmp) else Result.Failed("gambar hasil tidak valid")
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

    private fun encodeBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        scaledJpeg(bitmap).compress(Bitmap.CompressFormat.JPEG, 92, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaledJpeg(bitmap: Bitmap): Bitmap {
        val long = max(bitmap.width, bitmap.height)
        val scale = if (long > 1024) 1024f / long else 1f
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(8),
                (bitmap.height * scale).toInt().coerceAtLeast(8),
                true,
            )
        } else {
            bitmap
        }
    }

    /**
     * Uploads the picture and returns the server path Gradio expects in a FileData dict.
     *
     * `/upload` answers a bare JSON array such as `["/tmp/gradio/abc/floatpic.jpg"]`.
     */
    private fun upload(backend: AiBackend, bitmap: Bitmap, onLog: (String) -> Unit): String? {
        val url = "${backend.base}/gradio_api/upload"
        val boundary = "----floatpic${System.currentTimeMillis()}"
        val scaled = scaledJpeg(bitmap)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, baos)
        if (scaled !== bitmap) scaled.recycle()
        val image = baos.toByteArray()

        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Disposition: form-data; name=\"files\"; filename=\"floatpic.jpg\"\r\n".toByteArray())
            write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
            write(image)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        return try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            onLog("upload HTTP $code ${text.take(140)}")
            if (code !in 200..299) {
                throw IOException("HTTP $code saat mengunggah")
            }
            val arr = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull()
            arr?.firstOrNull()?.jsonPrimitive?.safeContent()
        } finally {
            conn.disconnect()
        }
    }

    private fun getBytes(url: String): ByteArray? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 120_000
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

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

                // collect frames of this stream; the request ends when `complete` arrives
                var found: Poll? = null

                fun handleFrame() {
                    if (found != null) return
                    if (dataLines.isEmpty()) {
                        event = ""
                        return
                    }
                    val raw = dataLines.joinToString("\n").trim()
                    dataLines.clear()
                    val type = event.trim()
                    event = ""
                    if (raw.isEmpty() || raw == "null") return
                    onLog("event=$type ${raw.take(120).replace('\n', ' ')}")

                    val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
                    val obj = when (parsed) {
                        is JsonArray -> parsed.firstOrNull() as? JsonObject
                        is JsonObject -> parsed
                        else -> null
                    }
                    when (type) {
                        "complete" -> {
                            if (obj != null) found = Poll.Done(obj)
                        }
                        "error" -> {
                            val msg = (obj?.get("error") ?: obj?.get("message"))
                                ?.jsonPrimitive?.safeContent()
                            found = Poll.Error(msg ?: "server mengembalikan error")
                        }
                        "generating", "status" -> progressText(type, obj)?.let { onStage(it) }
                    }
                    if (found == null && obj != null && looksLikeResult(obj) && type != "generating") {
                        found = Poll.Done(obj)
                    }
                }

                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (found != null) break
                        when {
                            line.isEmpty() -> handleFrame()
                            line.startsWith("event:") -> event = line.removePrefix("event:")
                            line.startsWith("data:") -> dataLines.add(line.removePrefix("data:").trim())
                        }
                    }
                }
                if (found == null) handleFrame()
                found?.let { return it }
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

    private fun looksLikeResult(obj: JsonObject): Boolean =
        obj["image"] != null || obj["url"] != null || obj["status"] != null

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
            raw.contains("Unable to resolve host") || raw.contains("UnknownHost") -> "tidak ada internet"
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
