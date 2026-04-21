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
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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
}




