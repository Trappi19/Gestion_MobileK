package com.example.gestion_mobilek

import android.database.sqlite.SQLiteDatabase

data class RepasDateConfig(
    val columnName: String,
    val isStorageDate: Boolean
)

object RepasDateCompat {

    private const val NEW_COL = "date_dernier_repas"
    private const val LEGACY_COL_1 = "nb_jour_repas"
    private const val LEGACY_COL_2 = "nb_jour_depuis_repas"

    fun resolve(db: SQLiteDatabase): RepasDateConfig {
        val cols = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(repas)", null).use { c ->
            if (c.moveToFirst()) {
                do {
                    cols.add(c.getString(1))
                } while (c.moveToNext())
            }
        }

        return when {
            cols.contains(NEW_COL) -> RepasDateConfig(NEW_COL, true)
            cols.contains(LEGACY_COL_1) -> RepasDateConfig(LEGACY_COL_1, false)
            cols.contains(LEGACY_COL_2) -> RepasDateConfig(LEGACY_COL_2, false)
            else -> RepasDateConfig(NEW_COL, true)
        }
    }

    fun storageOrderExpr(columnName: String): String {
        return "substr(printf('%08d', CAST($columnName AS INTEGER)), 5, 4) || " +
            "substr(printf('%08d', CAST($columnName AS INTEGER)), 3, 2) || " +
            "substr(printf('%08d', CAST($columnName AS INTEGER)), 1, 2)"
    }

    fun cursorDateAsStorage(config: RepasDateConfig, rawValue: String?): String? {
        return if (config.isStorageDate) {
            DateStorageUtils.normalizeStorageDate(rawValue)
        } else {
            DateStorageUtils.normalizeStorageDate(rawValue)
        }
    }
}

