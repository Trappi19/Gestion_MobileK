package com.example.gestion_mobilek

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

object FutureReminderStore {

    private const val TABLE = "future_repas_rappels"
    private const val SOURCE_MODE_LOCAL = 0
    private const val SOURCE_MODE_EXTERNAL = 1

    data class ReminderEntry(
        val id: Int,
        val mealId: Int,
        val sourceMode: Int,
        val triggerAtMillis: Long,
        val enabled: Boolean = true
    )

    private fun hasColumn(db: SQLiteDatabase, columnName: String): Boolean {
        db.rawQuery("PRAGMA table_info($TABLE)", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    if (cursor.getString(1) == columnName) return true
                } while (cursor.moveToNext())
            }
        }
        return false
    }

    fun ensureSchema(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = OFF")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                future_id INTEGER NOT NULL,
                trigger_at_millis INTEGER NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                source_mode INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        if (!hasColumn(db, "source_mode")) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN source_mode INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun cursorToEntry(cursor: android.database.Cursor): ReminderEntry {
        val sourceModeIndex = cursor.getColumnIndex("source_mode")
        return ReminderEntry(
            id = cursor.getInt(0),
            mealId = cursor.getInt(1),
            sourceMode = if (sourceModeIndex >= 0) cursor.getInt(sourceModeIndex) else SOURCE_MODE_LOCAL,
            triggerAtMillis = cursor.getLong(2),
            enabled = cursor.getInt(3) != 0
        )
    }

    fun loadForMeal(db: SQLiteDatabase, mealId: Int, sourceMode: Int): List<ReminderEntry> {
        ensureSchema(db)
        val out = mutableListOf<ReminderEntry>()
        db.rawQuery(
            "SELECT id, future_id, trigger_at_millis, enabled, source_mode FROM $TABLE WHERE future_id = ? AND source_mode = ? ORDER BY trigger_at_millis ASC",
            arrayOf(mealId.toString(), sourceMode.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    out.add(cursorToEntry(cursor))
                } while (cursor.moveToNext())
            }
        }
        return out
    }

    fun loadAllEnabled(db: SQLiteDatabase): List<ReminderEntry> {
        ensureSchema(db)
        val out = mutableListOf<ReminderEntry>()
        db.rawQuery(
            "SELECT id, future_id, trigger_at_millis, enabled, source_mode FROM $TABLE WHERE enabled = 1 ORDER BY trigger_at_millis ASC",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    out.add(cursorToEntry(cursor))
                } while (cursor.moveToNext())
            }
        }
        return out
    }

    fun loadById(db: SQLiteDatabase, reminderId: Int): ReminderEntry? {
        ensureSchema(db)
        db.rawQuery(
            "SELECT id, future_id, trigger_at_millis, enabled, source_mode FROM $TABLE WHERE id = ?",
            arrayOf(reminderId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToEntry(cursor)
            }
        }
        return null
    }

    fun replaceForMeal(db: SQLiteDatabase, mealId: Int, sourceMode: Int, reminderTimes: List<Long>) {
        ensureSchema(db)
        db.delete(
            TABLE,
            "future_id = ? AND source_mode = ?",
            arrayOf(mealId.toString(), sourceMode.toString())
        )
        reminderTimes.sorted().forEach { millis ->
            db.insert(TABLE, null, ContentValues().apply {
                put("future_id", mealId)
                put("trigger_at_millis", millis)
                put("enabled", 1)
                put("source_mode", sourceMode)
            })
        }
    }

    fun deleteForMeal(db: SQLiteDatabase, mealId: Int, sourceMode: Int) {
        ensureSchema(db)
        db.delete(
            TABLE,
            "future_id = ? AND source_mode = ?",
            arrayOf(mealId.toString(), sourceMode.toString())
        )
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
