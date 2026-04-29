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
    private const val KEY_DB_HOST = "db_host"
    private const val KEY_DB_PORT = "db_port"
    private const val KEY_DB_USER = "db_user"
    private const val KEY_DB_PASSWORD = "db_password"
    private const val KEY_DB_NAME_OVERRIDE = "db_name_override"

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

    fun getDbHost(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DB_HOST, null)?.takeIf { it.isNotBlank() }

    fun setDbHost(context: Context, host: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (host.isNullOrBlank()) remove(KEY_DB_HOST) else putString(KEY_DB_HOST, host)
        }
    }

    fun getDbPort(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_DB_PORT)) return null
        val port = prefs.getInt(KEY_DB_PORT, 0)
        return if (port > 0) port else null
    }

    fun setDbPort(context: Context, port: Int?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (port == null || port <= 0) remove(KEY_DB_PORT) else putInt(KEY_DB_PORT, port)
        }
    }

    fun getDbUser(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DB_USER, null)?.takeIf { it.isNotBlank() }

    fun setDbUser(context: Context, user: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (user.isNullOrBlank()) remove(KEY_DB_USER) else putString(KEY_DB_USER, user)
        }
    }

    fun getDbPassword(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DB_PASSWORD, null)

    fun setDbPassword(context: Context, password: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (password == null) remove(KEY_DB_PASSWORD) else putString(KEY_DB_PASSWORD, password)
        }
    }

    fun getDbNameOverride(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DB_NAME_OVERRIDE, null)?.takeIf { it.isNotBlank() }

    fun setDbNameOverride(context: Context, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (name.isNullOrBlank()) remove(KEY_DB_NAME_OVERRIDE) else putString(KEY_DB_NAME_OVERRIDE, name)
        }
    }
}


