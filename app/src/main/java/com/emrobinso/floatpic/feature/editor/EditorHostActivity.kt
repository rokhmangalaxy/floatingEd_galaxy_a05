package com.emrobinso.floatpic.feature.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.data.CrashLog
import com.emrobinso.floatpic.feature.bubble.BubbleService
import com.emrobinso.floatpic.feature.bubble.GalleryRequest

/**
 * Everything that arrives from OUTSIDE the app lands here, then hands the picture to the
 * overlay editor in [BubbleService] and finishes at once.
 *
 * The editor itself is no longer an Activity (it is a system overlay), but Android can only
 * deliver ACTION_SEND / ACTION_VIEW / a photo-picker result to an Activity — so this is the
 * thin, invisible doorway: receive, forward, finish.
 */
class EditorHostActivity : ComponentActivity() {

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) handOver(uri) else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // never show anything: this activity is a doorway, not a screen
        overridePendingTransition(0, 0)
        CrashLog.take(this)?.let { raw ->
            Toast.makeText(this, "crash sebelumnya: " + CrashLog.summary(raw), Toast.LENGTH_LONG).show()
        }
        when (intent?.action) {
            ACTION_PICK -> {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                val uri = ShareIntents.singleUri(intent) ?: ShareIntents.multiUris(intent).firstOrNull()
                if (uri != null) handOver(uri) else {
                    Toast.makeText(this, R.string.no_image, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            else -> {
                BubbleService.showEditor(this)
                finish()
            }
        }
    }

    private fun handOver(uri: Uri) {
        GalleryRequest.pending = false
        BubbleService.openInEditor(this, uri)
        finish()
    }

    companion object {
        const val ACTION_PICK = "com.emrobinso.floatpic.PICK_FROM_GALLERY"
    }
}
