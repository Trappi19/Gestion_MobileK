package com.example.gestion_mobilek

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
        val tvAbout = findViewById<TextView>(R.id.tvAbout)
        val cbReminderNotifications = findViewById<CheckBox>(R.id.cbReminderNotifications)

        cbReminderNotifications.isChecked = SettingsStore.areReminderNotificationsEnabled(this)
        cbReminderNotifications.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setReminderNotificationsEnabled(this, isChecked)
            refreshStatuses(tvNotificationStatus, tvAlarmStatus)
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
        findViewById<Button>(R.id.btnResyncReminders).setOnClickListener {
            FutureReminderScheduler.rescheduleAll(this)
            Toast.makeText(this, getString(R.string.settings_reminders_resynced), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnLicense).setOnClickListener {
            showLicenseDialog()
        }

        tvAbout.text = getAppVersionSummary()

        refreshStatuses(tvNotificationStatus, tvAlarmStatus)

        when {
            intent.getBooleanExtra("OPEN_LICENSE", false) -> showLicenseDialog()
            intent.getBooleanExtra("OPEN_ABOUT", false) -> showAboutDialog()
        }
    }

    private fun refreshStatuses(tvNotificationStatus: TextView, tvAlarmStatus: TextView) {
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
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_about_title)
            .setMessage(getString(R.string.settings_about_dialog_text, versionName, versionCode))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun getAppVersionSummary(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "?"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return getString(R.string.settings_about_summary, versionName, versionCode)
    }
}




