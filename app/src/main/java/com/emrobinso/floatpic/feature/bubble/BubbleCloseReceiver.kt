package com.emrobinso.floatpic.feature.bubble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BubbleCloseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == BubbleService.ACTION_CLOSE) {
            context.startService(
                Intent(context, BubbleService::class.java).setAction(BubbleService.ACTION_CLOSE),
            )
        }
    }
}
