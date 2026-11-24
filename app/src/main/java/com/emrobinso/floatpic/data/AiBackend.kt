package com.emrobinso.floatpic.data

/**
 * The AI servers the app can talk to.
 *
 * All of them run **Qwen-Image-Edit-2511** (the same base model as the space the app shipped
 * with); they differ in the wrapper, the LoRA set and how the argument list looks. Keeping
 * this as a table means switching servers is a one-line change, and the user can switch from
 * the panel when one of them is asleep.
 *
 * Two transports exist in the wild:
 *
 *  - [Kind.FILE_INPUT] — a normal Gradio Blocks app (`gr.Interface` / `demo.launch()`).
 *    The image must be uploaded first (`POST /gradio_api/upload`), then its server path is
 *    passed in the positional array as `{"path": ..., "meta": {"_type": "gradio.FileData"}}`.
 *    Results come back as a file dict with a `url` to download.
 *
 *  - [Kind.B64_JSON] — `gradio.Server` with a hand written `@app.api`, like the space this
 *    app used before. The image travels as a base64 string inside a JSON string and the
 *    result arrives as a `data:image/png;base64,...` URL.
 */
enum class AiBackend(
    val label: String,
    val base: String,
    val endpoint: String,
    val kind: Kind,
    /** shown in the log so a failure report is self-explanatory */
    val note: String,
) {
    /** 6 replicas, "None" LoRA, 4 steps — the fastest and least queued option. */
    RAPID_AIO(
        label = "Rapid-AIO",
        base = "https://aet256-qwen-image-edit-rapid-aio-loras-experimental.hf.space",
        endpoint = "/infer",
        kind = Kind.FILE_INPUT,
        note = "QIE-2511 + Rapid-AIO v19, LoRA opsional",
    ),

    /** The previous backend. Kept as a fallback: it accepts base64 and needs no upload. */
    LORAS_FAST(
        label = "LoRAs-Fast",
        base = "https://prithivmlmods-qwen-image-edit-2511-loras-fast.hf.space",
        endpoint = "/edit_image",
        kind = Kind.B64_JSON,
        note = "QIE-2511 + LoRA lazy load (tanpa opsi None)",
    ),
    ;

    enum class Kind { FILE_INPUT, B64_JSON }

    /** LoRA ids this backend accepts. */
    val loras: List<String>
        get() = when (this) {
            RAPID_AIO -> listOf(
                "Consistance", "Semirealistic-photo-detailer", "AnyPose", "Any2Real_2601",
                "Hyperrealistic-Portrait", "Ultrarealistic-Portrait", "BFS-Best-FaceSwap",
                "F2P", "Multiple-Angles", "Light-Restoration", "Relight",
                "Multi-Angle-Lighting", "Edit-Skin", "Next-Scene", "Flat-Log",
                "Upscale-Image", "Upscale2K",
            )
            LORAS_FAST -> listOf(
                "Photo-to-Anime", "Anime-V2", "Upscaler", "Unblur-Anything", "Polaroid-Photo",
                "Studio-DeLight", "Any-light", "Light-Migration", "Style-Transfer",
                "Ultra-Realistic-Portrait", "Multiple-Angles",
            )
        }

    /**
     * Adapter id for "no style". Rapid-AIO has a real `None`; LoRAs-Fast has none at all, so
     * it uses the least stylising adapter instead.
     */
    val noStyle: String?
        get() = when (this) {
            RAPID_AIO -> "None"
            LORAS_FAST -> "Unblur-Anything"
        }

    val callUrl: String get() = "$base/gradio_api/call${endpoint}"

    companion object {
        val DEFAULT = RAPID_AIO

        fun of(name: String?): AiBackend =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
