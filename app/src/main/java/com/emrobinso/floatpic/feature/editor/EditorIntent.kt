package com.emrobinso.floatpic.feature.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.emrobinso.floatpic.core.model.EditorState
import com.emrobinso.floatpic.core.model.Transform
import com.emrobinso.floatpic.ui.theme.AppTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val EXTRA_RESTORE_SESSION = "floatpic.extra.restore_session"

@Serializable
data class DraftSnapshot(
    val sourceUri: String?,
    val state: EditorState = EditorState(),
    val scale: Float = 1f,
    val theme: AppTheme = AppTheme.PASTEL_MAROON,
    val savedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun encode(s: DraftSnapshot): String = json.encodeToString(serializer(), s)
        fun decode(raw: String): DraftSnapshot? =
            runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
    }
}

object ShareIntents {

    fun editorIntentForShare(context: Context, uri: Uri): Intent =
        Intent(context, EditorActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun editorIntentForDraft(context: Context, sessionId: String): Intent =
        Intent(context, EditorActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_RESTORE_SESSION, sessionId)
        }

    fun shareImage(context: Context, uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(Intent.EXTRA_TITLE, "FloatPic")
            }
        }

    /** Compose a human friendly default when a share arrives with several images. */
    fun singleUri(intent: Intent): Uri? {
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }

    fun multiUris(intent: Intent): List<Uri> {
        @Suppress("DEPRECATION")
        val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        return list?.toList().orEmpty()
    }

    fun normalizedTransform(t: Transform): Transform = t.copy(rotation = t.rotation % 360f)
}
