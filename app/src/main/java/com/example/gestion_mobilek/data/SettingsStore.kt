package com.example.gestion_mobilek.data

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

import android.content.Context
import androidx.core.content.edit

object SettingsStore {
    private const val PREFS = "app_settings"
    private const val KEY_REMINDER_NOTIFICATIONS = "reminder_notifications_enabled"
    private const val KEY_EXTERNAL_SOURCE_ENABLED = "external_source_enabled"
    private const val KEY_EXTERNAL_DATABASE_NAME = "external_database_name"
    private const val KEY_KEEP_EXTERNAL_MODE = "keep_external_mode"

    fun areReminderNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMINDER_NOTIFICATIONS, true)
    }

    fun setReminderNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_REMINDER_NOTIFICATIONS, enabled) }
    }

    fun isExternalDataSourceEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXTERNAL_SOURCE_ENABLED, false)
    }

    fun setExternalDataSourceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_EXTERNAL_SOURCE_ENABLED, enabled) }
    }

    fun getExternalDatabaseName(context: Context): String? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EXTERNAL_DATABASE_NAME, null)
        return raw?.takeIf { it.isNotBlank() }
    }

    fun setExternalDatabaseName(context: Context, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (name.isNullOrBlank()) {
                remove(KEY_EXTERNAL_DATABASE_NAME)
            } else {
                putString(KEY_EXTERNAL_DATABASE_NAME, name)
            }
        }
    }

    fun shouldKeepExternalMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_EXTERNAL_MODE, false)
    }

    fun setKeepExternalMode(context: Context, keep: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_KEEP_EXTERNAL_MODE, keep) }
    }
}


