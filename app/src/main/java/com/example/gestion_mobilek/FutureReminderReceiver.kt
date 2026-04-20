package com.example.gestion_mobilek

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FutureReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            FutureReminderScheduler.ACTION_TRIGGER -> {
                val reminderId = intent.getIntExtra(FutureReminderScheduler.EXTRA_REMINDER_ID, -1)
                if (reminderId > 0) {
                    FutureReminderScheduler.showNotification(context, reminderId)
                }
            }
            FutureReminderScheduler.ACTION_SNOOZE -> {
                val reminderId = intent.getIntExtra(FutureReminderScheduler.EXTRA_REMINDER_ID, -1)
                if (reminderId > 0) {
                    FutureReminderScheduler.snoozeReminder(context, reminderId)
                }
            }
            FutureReminderScheduler.ACTION_DISMISS -> {
                val reminderId = intent.getIntExtra(FutureReminderScheduler.EXTRA_REMINDER_ID, -1)
                if (reminderId > 0) {
                    FutureReminderScheduler.dismissReminder(context, reminderId)
                }
            }
        }
    }
}

