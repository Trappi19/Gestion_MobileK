package com.example.gestion_mobilek

import android.app.Application

class GestionMobileKApp : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            ExternalSyncManager.requestBackgroundPush(applicationContext)
        }
    }
}

