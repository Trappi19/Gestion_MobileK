package com.example.gestion_mobilek

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

object FutureRecettesManager {

    private const val FUTURE_TABLE = "future_repas"
    const val NEW_DATE_COL = "date_dernier_repas"
    const val LEGACY_DATE_COL = "date_repas"

    data class SourceConfig(
        val tableName: String,
        val dateColumn: String,
        val isStorageDate: Boolean,
        val isExternalMode: Boolean
    )

    private fun dateSortExpr(column: String): String {
        return "substr(printf('%08d', CAST($column AS INTEGER)), 5, 4) || " +
            "substr(printf('%08d', CAST($column AS INTEGER)), 3, 2) || " +
            "substr(printf('%08d', CAST($column AS INTEGER)), 1, 2)"
    }

    fun ensureSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $FUTURE_TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nom_plat TEXT NOT NULL,
                id_personnes TEXT,
                $NEW_DATE_COL TEXT NOT NULL,
                description TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS _sync_history (
                table_name TEXT,
                pk_val TEXT,
                PRIMARY KEY (table_name, pk_val)
            )
            """.trimIndent()
        )
    }

    fun resolveDateColumn(db: SQLiteDatabase): String {
        ensureSchema(db)
        val cols = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($FUTURE_TABLE)", null).use { c ->
            if (c.moveToFirst()) {
                do {
                    cols.add(c.getString(1))
                } while (c.moveToNext())
            }
        }

        return when {
            cols.contains(NEW_DATE_COL) -> NEW_DATE_COL
            cols.contains(LEGACY_DATE_COL) -> LEGACY_DATE_COL
            else -> {
                db.execSQL("ALTER TABLE $FUTURE_TABLE ADD COLUMN $NEW_DATE_COL TEXT")
                NEW_DATE_COL
            }
        }
    }

    fun resolveSourceConfig(context: Context, db: SQLiteDatabase): SourceConfig {
        return SourceConfig(
            tableName = FUTURE_TABLE,
            dateColumn = resolveDateColumn(db),
            isStorageDate = true,
            isExternalMode = SettingsStore.isExternalDataSourceEnabled(context)
        )
    }

    fun resolveSourceTable(context: Context): String {
        return FUTURE_TABLE
    }

    fun migrateDueFutureRepas(context: Context, db: SQLiteDatabase) {
        if (SettingsStore.isExternalDataSourceEnabled(context)) return
        ensureSchema(db)
        val repasDateConfig = RepasDateCompat.resolve(db)
        val futureDateCol = resolveDateColumn(db)

        val todaySortable = DateStorageUtils.toSortable(DateStorageUtils.todayStorageDate()) ?: return
        val orderExpr = "SUBSTR($futureDateCol, 5) || SUBSTR($futureDateCol, 3, 2) || SUBSTR($futureDateCol, 1, 2)"

        try {
            val dueMeals = mutableListOf<ContentValues>()
            val dueIds = mutableListOf<Int>()

            val cursor = db.rawQuery(
                """SELECT id, nom_plat, id_personnes, $futureDateCol, description
                   FROM future_repas
                   WHERE $futureDateCol IS NOT NULL AND TRIM($futureDateCol) != ''
                     AND $orderExpr < ?""",
                arrayOf(todaySortable)
            )

            if (cursor.moveToFirst()) {
                do {
                    dueIds.add(cursor.getInt(0))
                    dueMeals.add(ContentValues().apply {
                        put("nom_plat", cursor.getString(1) ?: "")
                        put("id_personnes", cursor.getString(2) ?: "")
                        val rawDate = cursor.getString(3)
                        if (repasDateConfig.isStorageDate) {
                            put(repasDateConfig.columnName, DateStorageUtils.normalizeStorageDate(rawDate) ?: "")
                        } else {
                            put(repasDateConfig.columnName, DateStorageUtils.toLegacyDaysAgo(rawDate) ?: 0)
                        }
                        put("description", cursor.getString(4) ?: "")
                    })
                } while (cursor.moveToNext())
            }
            cursor.close()

            if (dueIds.isEmpty()) return

            dueIds.forEach { futureId ->
                FutureReminderScheduler.cancelMealReminders(context, futureId, 0, deleteRows = true)
            }

            db.beginTransaction()
            try {
                dueMeals.forEach { meal ->
                    db.insert("repas", null, meal)
                }
                dueIds.forEach { id ->
                    db.delete(FUTURE_TABLE, "id = ?", arrayOf(id.toString()))
                }
                dueMeals.forEach { meal ->
                    val idPersonnagesRaw = meal.getAsString("id_personnes") ?: ""
                    val personIds = idPersonnagesRaw.split(",").mapNotNull { it.trim().toIntOrNull() }
                    val mealDate = meal.getAsString(repasDateConfig.columnName) ?: ""
                    personIds.forEach { personId ->
                        val personCursor = db.rawQuery(
                            "SELECT dernier_passage FROM personnes WHERE id = ?",
                            arrayOf(personId.toString())
                        )
                        var shouldUpdate = true
                        if (personCursor.moveToFirst()) {
                            val existingDate = personCursor.getString(0)
                            if (!existingDate.isNullOrBlank()) {
                                val mealSort = if (repasDateConfig.isStorageDate) {
                                    DateStorageUtils.toSortable(mealDate) ?: ""
                                } else {
                                    val asStorage = DateStorageUtils.normalizeStorageDate(mealDate.toIntOrNull()?.toString() ?: "")
                                    DateStorageUtils.toSortable(asStorage) ?: ""
                                }
                                val existingSort = if (repasDateConfig.isStorageDate) {
                                    DateStorageUtils.toSortable(existingDate) ?: ""
                                } else {
                                    val asStorage = DateStorageUtils.normalizeStorageDate(existingDate.toIntOrNull()?.toString() ?: "")
                                    DateStorageUtils.toSortable(asStorage) ?: ""
                                }
                                shouldUpdate = mealSort > existingSort
                            }
                        }
                        personCursor.close()

                        if (shouldUpdate) {
                            val updateValues = ContentValues().apply {
                                put("dernier_passage", mealDate)
                            }
                            db.update("personnes", updateValues, "id = ?", arrayOf(personId.toString()))
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (_: SQLiteException) {
            // Ne bloque pas l'app si migration impossible temporairement
        }
    }
}
