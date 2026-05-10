package com.example.gestion_mobilek.sync

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
import android.util.Log

object ExternalSyncManager {

    @Volatile
    private var pushRunning = false

    fun isPushRunning(): Boolean = pushRunning

    fun pushToRemote(context: Context): Result<Int> {
        synchronized(this) {
            if (pushRunning) {
                return Result.failure(IllegalStateException("Synchronisation deja en cours"))
            }
            pushRunning = true
        }

        return try {
            val result = ExternalMariaDbSync.pushExternalToRemote(context)
            result.onSuccess {
                DatabaseHelper.closeActiveDatabase()
            }
            result
        } finally {
            synchronized(this) {
                pushRunning = false
            }
        }
    }

    fun requestBackgroundPush(context: Context) {
        if (!SettingsStore.isExternalDataSourceEnabled(context) || isPushRunning()) return

        Thread {
            val appCtx = context.applicationContext
            val pushResult = pushToRemote(appCtx)

            if (pushResult.isSuccess && SettingsStore.isSyncRemoteToLocalEnabled(appCtx)) {
                val pullResult = ExternalMariaDbSync.pullRemoteToLocal(appCtx)
                pullResult
                    .onSuccess { count ->
                        Log.d("ExternalSyncManager", "pullRemoteToLocal OK : $count tables copiées")
                        val localDb = DatabaseHelper(appCtx).getDatabaseForMode(useExternal = false)
                        FutureReminderStore.loadAll(localDb).forEach { reminder ->
                            FutureReminderScheduler.scheduleReminder(appCtx, reminder)
                        }
                    }
                    .onFailure { e ->
                        Log.d("ExternalSyncManager", "pullRemoteToLocal échec : ${e.message}")
                    }
            }
        }.start()
    }
}

