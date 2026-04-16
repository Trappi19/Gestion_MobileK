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
        // Recopy la BDD au premier acces de chaque lancement d'app
        if (dbInstance == null || !dbInstance!!.isOpen) {
            if (!copied) {
                copyDatabaseFromAssets()
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

        // Ecrase la base locale a chaque lancement pour repartir de assets/bdd.db
        if (dbFile.exists()) dbFile.delete()
        context.getDatabasePath("$DB_NAME-wal").delete()
        context.getDatabasePath("$DB_NAME-shm").delete()
        context.getDatabasePath("$DB_NAME-journal").delete()

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
