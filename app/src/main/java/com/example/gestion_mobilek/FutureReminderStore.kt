package com.example.gestion_mobilek

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

object FutureReminderStore {

    private const val TABLE = "future_repas_rappels"

    data class ReminderEntry(
        val id: Int,
        val futureId: Int,
        val triggerAtMillis: Long,
        val enabled: Boolean = true
    )

    fun ensureSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                future_id INTEGER NOT NULL,
                trigger_at_millis INTEGER NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(future_id) REFERENCES future_repas(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    fun loadForFuture(db: SQLiteDatabase, futureId: Int): List<ReminderEntry> {
        ensureSchema(db)
        val out = mutableListOf<ReminderEntry>()
        db.rawQuery(
            "SELECT id, future_id, trigger_at_millis, enabled FROM $TABLE WHERE future_id = ? ORDER BY trigger_at_millis ASC",
            arrayOf(futureId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    out.add(
                        ReminderEntry(
                            id = cursor.getInt(0),
                            futureId = cursor.getInt(1),
                            triggerAtMillis = cursor.getLong(2),
                            enabled = cursor.getInt(3) != 0
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        return out
    }

    fun loadAllEnabled(db: SQLiteDatabase): List<ReminderEntry> {
        ensureSchema(db)
        val out = mutableListOf<ReminderEntry>()
        db.rawQuery(
            "SELECT id, future_id, trigger_at_millis, enabled FROM $TABLE WHERE enabled = 1 ORDER BY trigger_at_millis ASC",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    out.add(
                        ReminderEntry(
                            id = cursor.getInt(0),
                            futureId = cursor.getInt(1),
                            triggerAtMillis = cursor.getLong(2),
                            enabled = cursor.getInt(3) != 0
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        return out
    }

    fun loadById(db: SQLiteDatabase, reminderId: Int): ReminderEntry? {
        ensureSchema(db)
        db.rawQuery(
            "SELECT id, future_id, trigger_at_millis, enabled FROM $TABLE WHERE id = ?",
            arrayOf(reminderId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return ReminderEntry(
                    id = cursor.getInt(0),
                    futureId = cursor.getInt(1),
                    triggerAtMillis = cursor.getLong(2),
                    enabled = cursor.getInt(3) != 0
                )
            }
        }
        return null
    }

    fun replaceForFuture(db: SQLiteDatabase, futureId: Int, reminderTimes: List<Long>) {
        ensureSchema(db)
        db.delete(TABLE, "future_id = ?", arrayOf(futureId.toString()))
        reminderTimes.sorted().forEach { millis ->
            db.insert(TABLE, null, ContentValues().apply {
                put("future_id", futureId)
                put("trigger_at_millis", millis)
                put("enabled", 1)
            })
        }
    }

    fun deleteForFuture(db: SQLiteDatabase, futureId: Int) {
        ensureSchema(db)
        db.delete(TABLE, "future_id = ?", arrayOf(futureId.toString()))
    }

    fun deleteReminder(db: SQLiteDatabase, reminderId: Int) {
        ensureSchema(db)
        db.delete(TABLE, "id = ?", arrayOf(reminderId.toString()))
    }

    fun updateReminderTime(db: SQLiteDatabase, reminderId: Int, triggerAtMillis: Long) {
        ensureSchema(db)
        val values = ContentValues().apply {
            put("trigger_at_millis", triggerAtMillis)
            put("enabled", 1)
        }
        db.update(TABLE, values, "id = ?", arrayOf(reminderId.toString()))
    }

    fun setEnabled(db: SQLiteDatabase, reminderId: Int, enabled: Boolean) {
        ensureSchema(db)
        val values = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
        }
        db.update(TABLE, values, "id = ?", arrayOf(reminderId.toString()))
    }
}

