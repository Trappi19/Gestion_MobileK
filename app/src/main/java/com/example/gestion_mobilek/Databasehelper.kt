package com.example.gestion_mobilek

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "bdd.db"          // TON FICHIER
        private const val DB_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Vide, car BDD déjà créée dans DBeaver
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // À gérer plus tard si changement de structure
    }

    fun getDatabase(): SQLiteDatabase {
        createOrOverwriteDatabase()
        return SQLiteDatabase.openDatabase(
            context.getDatabasePath(DB_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
    }

    // DEV : recopie à CHAQUE lancement (comme ton Unity)
    private fun createOrOverwriteDatabase() {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.parentFile.exists()) {
            dbFile.parentFile?.mkdirs()
        }
        copyDatabaseFromAssets(dbFile)
    }

    private fun copyDatabaseFromAssets(outFile: File) {
        try {
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(outFile).use { output ->
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
