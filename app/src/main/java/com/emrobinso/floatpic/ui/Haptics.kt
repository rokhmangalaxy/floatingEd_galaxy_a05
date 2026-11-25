package com.emrobinso.floatpic.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Central haptics: every "snap" or limit in the editor goes through here. */
class Haptics(private val view: View?) {

    fun tick() {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun snap() {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun confirm() {
        view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun reject() {
        view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    companion object {
        val Noop = Haptics(null)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
