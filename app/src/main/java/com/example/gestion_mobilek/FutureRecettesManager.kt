package com.example.gestion_mobilek

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

object FutureRecettesManager {

    private const val FUTURE_TABLE = "future_repas"

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
                date_repas TEXT NOT NULL,
                description TEXT
            )
            """.trimIndent()
        )
    }

    fun migrateDueFutureRepas(db: SQLiteDatabase) {
        ensureSchema(db)
        val repasDateConfig = RepasDateCompat.resolve(db)

        val todaySortable = DateStorageUtils.toSortable(DateStorageUtils.todayStorageDate()) ?: return
        val orderExpr = dateSortExpr("date_repas")

        try {
            val dueMeals = mutableListOf<ContentValues>()
            val dueIds = mutableListOf<Int>()

            val cursor = db.rawQuery(
                """SELECT id, nom_plat, id_personnes, date_repas, description
                   FROM $FUTURE_TABLE
                   WHERE $orderExpr < ?""",
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

            db.beginTransaction()
            try {
                dueMeals.forEach { meal ->
                    db.insert("repas", null, meal)
                }
                dueIds.forEach { id ->
                    db.delete(FUTURE_TABLE, "id = ?", arrayOf(id.toString()))
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

