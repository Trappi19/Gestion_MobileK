package com.example.gestion_mobilek.data

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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.FileOutputStream
import java.io.IOException

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, LOCAL_DB_NAME, null, DB_VERSION) {

    companion object {
        private const val LOCAL_DB_NAME = "bdd.db"
        const val EXTERNAL_CACHE_DB_NAME = "bdd_online.db"
        private const val DB_VERSION = 1
        private var localDbInstance: SQLiteDatabase? = null
        private var externalDbInstance: SQLiteDatabase? = null

        @Synchronized
        fun closeActiveDatabase() {
            localDbInstance?.takeIf { it.isOpen }?.close()
            localDbInstance = null
            externalDbInstance?.takeIf { it.isOpen }?.close()
            externalDbInstance = null
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun getDatabase(): SQLiteDatabase {
        return getDatabaseForMode(SettingsStore.isExternalDataSourceEnabled(context))
    }

    @Synchronized
    fun getDatabaseForMode(useExternal: Boolean): SQLiteDatabase {
        val targetName = if (useExternal) EXTERNAL_CACHE_DB_NAME else LOCAL_DB_NAME
        ensureDatabaseExists(targetName)

        if (useExternal) {
            if (externalDbInstance == null || !externalDbInstance!!.isOpen) {
                val dbPath = context.getDatabasePath(targetName).path
                externalDbInstance = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
            }
            return externalDbInstance!!
        } else {
            if (localDbInstance == null || !localDbInstance!!.isOpen) {
                val dbPath = context.getDatabasePath(targetName).path
                localDbInstance = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
            }
            return localDbInstance!!
        }
    }

    private fun ensureDatabaseExists(dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            copyDatabaseFromAssets(dbName)
        }
    }

    private fun copyDatabaseFromAssets(targetDbName: String) {
        val dbFile = context.getDatabasePath(targetDbName)
        if (!dbFile.parentFile!!.exists()) dbFile.parentFile!!.mkdirs()

        if (dbFile.exists()) return

        try {
            context.assets.open(LOCAL_DB_NAME).use { input ->
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
