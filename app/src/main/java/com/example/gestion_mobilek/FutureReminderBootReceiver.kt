package com.example.gestion_mobilek

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FutureReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            FutureReminderScheduler.rescheduleAll(context)
        }
    }
}

