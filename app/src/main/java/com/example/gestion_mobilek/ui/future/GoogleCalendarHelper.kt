package com.example.gestion_mobilek.ui.future

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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime

object GoogleCalendarHelper {

    private const val EVENT_DURATION_HOURS = 2L
    const val COLUMN_GOOGLE_EVENT_ID = "google_event_id"

    // ─── Assure que la colonne google_event_id existe dans future_repas ──

    fun ensureGoogleEventIdColumn(db: SQLiteDatabase) {
        try {
            val cols = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(future_repas)", null).use { c ->
                if (c.moveToFirst()) {
                    do { cols.add(c.getString(1)) } while (c.moveToNext())
                }
            }
            if (!cols.contains(COLUMN_GOOGLE_EVENT_ID)) {
                db.execSQL("ALTER TABLE future_repas ADD COLUMN $COLUMN_GOOGLE_EVENT_ID TEXT")
            }
        } catch (_: SQLiteException) {}
    }

    // ─── Construit le service Calendar Google ─────────────────────────────

    fun buildCalendarService(context: Context, accountName: String): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(CalendarScopes.CALENDAR_EVENTS)
        )
        credential.selectedAccountName = accountName

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("GestionMobileK")
            .build()
    }

    // ─── Crée un événement dans Google Calendar ───────────────────────────

    fun createEvent(
        service: Calendar,
        nomPlat: String,
        personnesNoms: String,
        description: String,
        dateStorage: String
    ): String {
        val startMillis = storageToMillis(dateStorage)
        val endMillis = startMillis + EVENT_DURATION_HOURS * 3600_000L

        val event = Event().apply {
            summary = nomPlat.ifBlank { "Repas planifié" }
            this.description = buildString {
                append("Personnes : $personnesNoms")
                if (description.isNotBlank()) append("\n\n$description")
            }
            start = EventDateTime().apply {
                dateTime = DateTime(startMillis)
                timeZone = java.util.TimeZone.getDefault().id
            }
            end = EventDateTime().apply {
                dateTime = DateTime(endMillis)
                timeZone = java.util.TimeZone.getDefault().id
            }
        }

        val created = service.events().insert("primary", event).execute()
        return created.id ?: ""
    }

    // ─── Met à jour un événement existant ─────────────────────────────────

    fun updateEvent(
        service: Calendar,
        eventId: String,
        nomPlat: String,
        personnesNoms: String,
        description: String,
        dateStorage: String
    ) {
        val startMillis = storageToMillis(dateStorage)
        val endMillis = startMillis + EVENT_DURATION_HOURS * 3600_000L

        val event = Event().apply {
            summary = nomPlat.ifBlank { "Repas planifié" }
            this.description = buildString {
                append("Personnes : $personnesNoms")
                if (description.isNotBlank()) append("\n\n$description")
            }
            start = EventDateTime().apply {
                dateTime = DateTime(startMillis)
                timeZone = java.util.TimeZone.getDefault().id
            }
            end = EventDateTime().apply {
                dateTime = DateTime(endMillis)
                timeZone = java.util.TimeZone.getDefault().id
            }
        }

        service.events().update("primary", eventId, event).execute()
    }

    // ─── Sauvegarde l'eventId dans la BDD ────────────────────────────────

    fun saveGoogleEventId(db: SQLiteDatabase, futureId: Int, eventId: String, table: String) {
        val values = ContentValues().apply { put(COLUMN_GOOGLE_EVENT_ID, eventId) }
        db.update(table, values, "id = ?", arrayOf(futureId.toString()))
    }

    // ─── Lecture de l'eventId depuis la BDD ──────────────────────────────

    fun readGoogleEventId(db: SQLiteDatabase, futureId: Int, table: String): String? {
        return try {
            ensureGoogleEventIdColumn(db)
            var result: String? = null
            db.rawQuery(
                "SELECT $COLUMN_GOOGLE_EVENT_ID FROM $table WHERE id = ?",
                arrayOf(futureId.toString())
            ).use { c ->
                if (c.moveToFirst()) result = c.getString(0)?.takeIf { it.isNotBlank() }
            }
            result
        } catch (_: SQLiteException) { null }
    }

    // ─── Convertit dateStorage DDMMYYYY → milliseconds (minuit heure locale)

    private fun storageToMillis(dateStorage: String): Long {
        val normalized = DateStorageUtils.normalizeStorageDate(dateStorage) ?: return System.currentTimeMillis()
        val day = normalized.substring(0, 2).toIntOrNull() ?: return System.currentTimeMillis()
        val month = normalized.substring(2, 4).toIntOrNull() ?: return System.currentTimeMillis()
        val year = normalized.substring(4, 8).toIntOrNull() ?: return System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, day)
            set(java.util.Calendar.HOUR_OF_DAY, 19)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
