package com.example.gestion_mobilek.ui.settings

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

import android.app.AlertDialog
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 5401
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val tvNotificationStatus = findViewById<TextView>(R.id.tvNotificationStatus)
        val tvAlarmStatus = findViewById<TextView>(R.id.tvAlarmStatus)
        val tvDataSourceStatus = findViewById<TextView>(R.id.tvDataSourceStatus)
        val tvRemoteDbName = findViewById<TextView>(R.id.tvRemoteDbName)
        val tvAbout = findViewById<TextView>(R.id.tvAbout)
        val cbReminderNotifications = findViewById<CheckBox>(R.id.cbReminderNotifications)
        val cbKeepExternalMode = findViewById<CheckBox>(R.id.cbKeepExternalMode)

        cbReminderNotifications.isChecked = SettingsStore.areReminderNotificationsEnabled(this)
        cbReminderNotifications.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setReminderNotificationsEnabled(this, isChecked)
            refreshStatuses(tvNotificationStatus, tvAlarmStatus, tvDataSourceStatus, tvRemoteDbName)
        }

        cbKeepExternalMode.isChecked = SettingsStore.shouldKeepExternalMode(this)
        cbKeepExternalMode.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setKeepExternalMode(this, isChecked)
            refreshStatuses(tvNotificationStatus, tvAlarmStatus, tvDataSourceStatus, tvRemoteDbName)
        }

        findViewById<Button>(R.id.btnRequestNotifications).setOnClickListener {
            requestNotificationPermission()
        }
        findViewById<Button>(R.id.btnOpenAppSettings).setOnClickListener {
            openAppSettings()
        }
        findViewById<Button>(R.id.btnOpenAlarmSettings).setOnClickListener {
            openAlarmSettings()
        }
        findViewById<Button>(R.id.btnOnlineDiagnostic).setOnClickListener {
            startActivity(Intent(this, OnlineDiagnosticActivity::class.java))
        }
        findViewById<Button>(R.id.btnResyncReminders).setOnClickListener {
            FutureReminderScheduler.rescheduleAll(this)
            Toast.makeText(this, getString(R.string.settings_reminders_resynced), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnLicense).setOnClickListener {
            showLicenseDialog()
        }
        findViewById<Button>(R.id.btnEditDbConnection).setOnClickListener {
            showEditDbConnectionDialog()
        }
        findViewById<Button>(R.id.btnInitRemoteDb).setOnClickListener {
            showInitDbConfirmDialog()
        }

        tvAbout.text = getAppVersionSummary()

        refreshStatuses(tvNotificationStatus, tvAlarmStatus, tvDataSourceStatus, tvRemoteDbName)

        when {
            intent.getBooleanExtra("OPEN_LICENSE", false) -> showLicenseDialog()
            intent.getBooleanExtra("OPEN_ABOUT", false) -> showAboutDialog()
        }
    }

    private fun refreshStatuses(
        tvNotificationStatus: TextView,
        tvAlarmStatus: TextView,
        tvDataSourceStatus: TextView,
        tvRemoteDbName: TextView
    ) {
        val notificationsGranted = if (Build.VERSION.SDK_INT < 33) {
            true
        } else {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        tvNotificationStatus.text = getString(
            R.string.settings_notification_status,
            if (notificationsGranted) getString(R.string.settings_status_granted) else getString(R.string.settings_status_missing)
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val exactAlarmAllowed = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            alarmManager.canScheduleExactAlarms()
        }
        tvAlarmStatus.text = getString(
            R.string.settings_alarm_status,
            if (exactAlarmAllowed) getString(R.string.settings_status_granted) else getString(R.string.settings_status_missing)
        )

        val dataSourceText = if (SettingsStore.isExternalDataSourceEnabled(this)) {
            getString(R.string.settings_data_source_external)
        } else {
            getString(R.string.settings_data_source_local)
        }
        tvDataSourceStatus.text = getString(R.string.settings_data_source_status, dataSourceText)

        val dbName = SettingsStore.getExternalDatabaseName(this)
        tvRemoteDbName.text = getString(
            R.string.settings_remote_database,
            dbName ?: getString(R.string.settings_remote_database_unknown)
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, getString(R.string.settings_notifications_not_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.settings_status_granted), Toast.LENGTH_SHORT).show()
            return
        }
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun openAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, getString(R.string.settings_alarm_not_required), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun showLicenseDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_license)
            .setMessage(getString(R.string.settings_license_text))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAboutDialog() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "?"

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_about_title)
            .setMessage(getString(R.string.settings_about_dialog_text, versionName))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun getAppVersionSummary(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "?"
        return getString(R.string.settings_about_summary, versionName)
    }

    private fun showEditDbConnectionDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_db_connection, null)
        val etHost = view.findViewById<TextInputEditText>(R.id.etDbHost)
        val etPort = view.findViewById<TextInputEditText>(R.id.etDbPort)
        val etUser = view.findViewById<TextInputEditText>(R.id.etDbUser)
        val etPassword = view.findViewById<TextInputEditText>(R.id.etDbPassword)
        val etDatabase = view.findViewById<TextInputEditText>(R.id.etDbDatabase)

        etHost.setText(SettingsStore.getDbHost(this) ?: com.example.gestion_mobilek.BuildConfig.MARIADB_HOST.trim())
        val currentPort = SettingsStore.getDbPort(this) ?: com.example.gestion_mobilek.BuildConfig.MARIADB_PORT.takeIf { it > 0 }
        etPort.setText(currentPort?.toString() ?: "3306")
        etUser.setText(SettingsStore.getDbUser(this) ?: com.example.gestion_mobilek.BuildConfig.MARIADB_USER.trim())
        etDatabase.setText(
            SettingsStore.getDbNameOverride(this)
                ?: com.example.gestion_mobilek.BuildConfig.MARIADB_DATABASE.trim()
        )
        val hasStoredPassword = SettingsStore.getDbPassword(this) != null
        if (hasStoredPassword) {
            etPassword.setHint(R.string.db_config_password_saved)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_edit_db_connection)
            .setView(view)
            .setPositiveButton(R.string.db_config_save) { _, _ ->
                val host = etHost.text?.toString()?.trim() ?: ""
                val portText = etPort.text?.toString()?.trim() ?: ""
                val user = etUser.text?.toString()?.trim() ?: ""
                val password = etPassword.text?.toString()
                val database = etDatabase.text?.toString()?.trim() ?: ""

                val port = portText.toIntOrNull()
                if (host.isBlank() || port == null || port <= 0 || user.isBlank() || database.isBlank()) {
                    Toast.makeText(this, getString(R.string.db_config_error_required), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                SettingsStore.setDbHost(this, host)
                SettingsStore.setDbPort(this, port)
                SettingsStore.setDbUser(this, user)
                if (!password.isNullOrEmpty()) {
                    SettingsStore.setDbPassword(this, password)
                }
                SettingsStore.setDbNameOverride(this, database)

                Toast.makeText(this, getString(R.string.db_config_saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInitDbConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.db_init_title)
            .setMessage(R.string.db_init_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> checkAndInitRemoteDb() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun checkAndInitRemoteDb() {
        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.db_init_checking)
            .setCancelable(false)
            .create()
        progress.show()

        Thread {
            val result = runCatching { ExternalMariaDbSync.checkRemoteDbExists(applicationContext) }
            runOnUiThread {
                progress.dismiss()
                result
                    .onSuccess { check ->
                        when (check) {
                            is ExternalMariaDbSync.DbCheckResult.NotExists -> performDbInit(dropIfExists = false)
                            is ExternalMariaDbSync.DbCheckResult.Exists -> showReInitConfirmDialog(check.name)
                        }
                    }
                    .onFailure { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.db_init_error, e.message ?: "?"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }.start()
    }

    private fun showReInitConfirmDialog(dbName: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.db_reinit_title)
            .setMessage(getString(R.string.db_reinit_confirm_message, dbName))
            .setPositiveButton(R.string.db_reinit_confirm) { _, _ -> performDbInit(dropIfExists = true) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performDbInit(dropIfExists: Boolean) {
        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.db_init_in_progress)
            .setCancelable(false)
            .create()
        progress.show()

        Thread {
            val result = runCatching { ExternalMariaDbSync.initRemoteDatabase(applicationContext, dropIfExists) }
            runOnUiThread {
                progress.dismiss()
                result
                    .onSuccess {
                        Toast.makeText(this, getString(R.string.db_init_success), Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.db_init_error, e.message ?: "?"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }.start()
    }
}




