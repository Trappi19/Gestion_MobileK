package com.example.gestion_mobilek

import android.content.Context
import androidx.core.content.edit

object SettingsStore {
    private const val PREFS = "app_settings"
    private const val KEY_REMINDER_NOTIFICATIONS = "reminder_notifications_enabled"

    fun areReminderNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMINDER_NOTIFICATIONS, true)
    }

    fun setReminderNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_REMINDER_NOTIFICATIONS, enabled) }
    }
}


