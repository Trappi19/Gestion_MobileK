package com.example.gestion_mobilek.reminders

import com.example.gestion_mobilek.R
import com.example.gestion_mobilek.app.*
import com.example.gestion_mobilek.data.*
import com.example.gestion_mobilek.reminders.*
import com.example.gestion_mobilek.sync.*
import com.example.gestion_mobilek.ui.common.*
import com.example.gestion_mobilek.ui.future.*
import com.example.gestion_mobilek.ui.history.*
import com.example.gestion_mobilek.ui.items.*
import com.example.gestion_mobilek.ui.main.*
import com.example.gestion_mobilek.ui.persons.*
import com.example.gestion_mobilek.ui.settings.*
import com.example.gestion_mobilek.utils.*

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

