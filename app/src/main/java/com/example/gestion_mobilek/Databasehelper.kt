package com.example.gestion_mobilek

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.FileOutputStream
import java.io.IOException

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "bdd.db"
        private const val DB_VERSION = 1
        private var dbInstance: SQLiteDatabase? = null  // Instance unique
        private var copied = false  // Flag copie par session
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun getDatabase(): SQLiteDatabase {
        // Ferme et rouvre seulement si pas encore fait dans cette session
        if (dbInstance == null || !dbInstance!!.isOpen) {
            if (!copied) {
                copyDatabaseFromAssets()  // Copie UNE SEULE fois au 1er appel
                copied = true
            }
            val dbPath = context.getDatabasePath(DB_NAME).path
            dbInstance = SQLiteDatabase.openDatabase(
                dbPath, null, SQLiteDatabase.OPEN_READWRITE
            )
        }
        return dbInstance!!
    }

    private fun copyDatabaseFromAssets() {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.parentFile!!.exists()) dbFile.parentFile!!.mkdirs()
        if (dbFile.exists()) return

        try {
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(dbFile).use { output ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (input.read(buffer).also { length = it } > 0) {
                        output.write(buffer, 0, length)
                    }
                    output.flush()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
