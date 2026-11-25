package com.emrobinso.floatpic.feature.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emrobinso.floatpic.R
import com.emrobinso.floatpic.data.CrashLog
import com.emrobinso.floatpic.feature.bubble.BubbleService
import com.emrobinso.floatpic.ui.icons.FloatIcons
import com.emrobinso.floatpic.ui.theme.AppTheme
import com.emrobinso.floatpic.ui.theme.FloatPicTheme

/**
 * The launcher screen: the bubble permission first, then the editor.
 *
 * The old build went straight into the editor on launch, so the overlay permission — the one
 * thing the bubble cannot work without — was buried behind "minimize" and the bubble never
 * appeared. This screen makes the permission a visible step, then opens the editor.
 */
class HomeActivity : ComponentActivity() {

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // the system page reports nothing useful; the toggle is the source of truth
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // a crash in a release build is otherwise invisible; surface the previous one
        CrashLog.take(this)?.let { raw ->
            Toast.makeText(this, "crash sebelumnya: " + CrashLog.summary(raw), Toast.LENGTH_LONG).show()
        }

        setContent {
            FloatPicTheme(theme = AppTheme.MAROON_DARK, dark = false) {
                var granted by remember { mutableStateOf(BubbleService.canDrawOverlays(this)) }
                var running by remember { mutableStateOf(BubbleService.isRunning) }
                val lifecycleOwner = LocalLifecycleOwner.current

                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            granted = BubbleService.canDrawOverlays(this@HomeActivity)
                            running = BubbleService.isRunning
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF160810), Color(0xFF2A0D18), Color(0xFF120A0D)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .heightIn(max = 620.dp),
                    ) {
                        Column(
                            Modifier
                                .padding(22.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .15f)),
                                    contentAlignment = Alignment.Center,
                                ) { FloatIcons.Sparkle(tint = MaterialTheme.colorScheme.primary) }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "FloatPic",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                getString(R.string.home_tagline),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))

                            // step 1: overlay permission
                            StepCard(
                                number = "1",
                                title = getString(R.string.home_step_permission),
                                body = getString(
                                    if (granted) R.string.home_permission_on else R.string.home_permission_off,
                                ),
                                done = granted,
                                action = getString(if (granted) R.string.home_done else R.string.home_grant),
                                actionEnabled = !granted,
                                onClick = { requestOverlay() },
                            )
                            Spacer(Modifier.height(8.dp))

                            // step 2: the switch
                            StepCard(
                                number = "2",
                                title = getString(R.string.home_step_bubble),
                                body = getString(
                                    if (running) R.string.home_bubble_on else R.string.home_bubble_off,
                                ),
                                done = running,
                                action = getString(
                                    if (running) R.string.home_stop else R.string.home_start,
                                ),
                                actionEnabled = granted,
                                destructive = running,
                                onClick = {
                                    if (running) {
                                        BubbleService.hide(this@HomeActivity)
                                        running = false
                                    } else {
                                        BubbleService.startEmpty(this@HomeActivity)
                                        running = true
                                    }
                                },
                            )
                            Spacer(Modifier.height(8.dp))

                            // step 3: the editor
                            StepCard(
                                number = "3",
                                title = getString(R.string.home_step_editor),
                                body = getString(R.string.home_editor_body),
                                done = false,
                                action = getString(R.string.home_open),
                                actionEnabled = true,
                                onClick = { openEditor() },
                            )

                            Spacer(Modifier.height(14.dp))
                            Text(
                                getString(R.string.home_footer),
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                textAlign = TextAlign.Start,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestOverlay() {
        val launched = runCatching {
            overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }.isSuccess
        if (!launched) {
            runCatching {
                overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }.onFailure {
                Toast.makeText(this, R.string.bubble_permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openEditor() {
        // the editor is an overlay window now, so nothing has to be launched on top of it
        BubbleService.showEditor(this)
        // step back to the home screen: the editor floats above whatever the user was doing
        finish()
    }
}

@Composable
private fun StepCard(
    number: String,
    title: String,
    body: String,
    done: Boolean,
    action: String,
    actionEnabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant.copy(alpha = .38f))
            .border(1.dp, scheme.outline.copy(alpha = .22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (done) scheme.primary else scheme.onSurfaceVariant.copy(alpha = .18f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else number,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (done) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                body,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            action,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (actionEnabled) scheme.onPrimary else scheme.onSurfaceVariant.copy(.6f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        !actionEnabled -> scheme.surfaceVariant.copy(alpha = .5f)
                        destructive -> scheme.error
                        else -> scheme.primary
                    },
                )
                .clickable(enabled = actionEnabled, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
