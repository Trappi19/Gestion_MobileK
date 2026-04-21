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

import android.content.Context
import android.database.sqlite.SQLiteException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FutureReminderFormatter {

    data class NotificationPayload(
        val title: String,
        val text: String,
        val bigText: String
    )

    private fun shortList(values: List<String>): String {
        val cleaned = values.filter { it.isNotBlank() }
        return when {
            cleaned.isEmpty() -> "Aucun"
            cleaned.size <= 2 -> cleaned.joinToString(", ")
            else -> "${cleaned[0]}, ${cleaned[1]} +"
        }
    }

    private fun normalizeCsv(value: String?): List<String> =
        value.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun formatDateTime(millis: Long): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun buildPayload(context: Context, sourceMode: Int, mealId: Int, reminderAtMillis: Long): NotificationPayload? {
        return try {
            val db = DatabaseHelper(context).getDatabaseForMode(sourceMode != 0)
            val sourceTable = if (sourceMode != 0) "repas" else "future_repas"
            val dateColumn = if (sourceMode != 0) RepasDateCompat.resolve(db).columnName else FutureRecettesManager.resolveDateColumn(db)

            db.rawQuery(
                "SELECT nom_plat, id_personnes, $dateColumn FROM $sourceTable WHERE id = ?",
                arrayOf(mealId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null

                val plats = normalizeCsv(cursor.getString(0))
                val personIds = normalizeCsv(cursor.getString(1))
                val mealDate = DateStorageUtils.displayFromStorage(DateStorageUtils.normalizeStorageDate(cursor.getString(2)))

                val personNames = mutableListOf<String>()
                personIds.forEach { id ->
                    db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(id)).use { personCursor ->
                        if (personCursor.moveToFirst()) {
                            personNames.add(personCursor.getString(0) ?: id)
                        }
                    }
                }

                val personsLine = shortList(personNames)
                val platsLine = shortList(plats)
                val reminderLine = formatDateTime(reminderAtMillis)

                NotificationPayload(
                    title = context.getString(R.string.future_reminder_notification_title),
                    text = "$personsLine • $platsLine",
                    bigText = "Personnes: $personsLine\nPlats: $platsLine\nRepas prévu: $mealDate\nRappel: $reminderLine"
                )
            }
        } catch (_: SQLiteException) {
            null
        }
    }
}

