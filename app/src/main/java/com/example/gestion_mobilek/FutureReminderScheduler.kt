package com.example.gestion_mobilek

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object FutureReminderScheduler {

    const val CHANNEL_ID = "future_reminders"
    private const val CHANNEL_NAME = "Rappels de recettes"
    private const val CHANNEL_DESCRIPTION = "Notifications pour les recettes planifiées"

    const val ACTION_TRIGGER = "com.example.gestion_mobilek.ACTION_TRIGGER_REMINDER"
    const val ACTION_DISMISS = "com.example.gestion_mobilek.ACTION_DISMISS_REMINDER"
    const val ACTION_SNOOZE = "com.example.gestion_mobilek.ACTION_SNOOZE_REMINDER"

    const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
    const val EXTRA_FUTURE_ID = "EXTRA_FUTURE_ID"

    private const val SNOOZE_DELAY_MS = 60 * 60 * 1000L

    private fun reminderDb(context: Context) = DatabaseHelper(context).getDatabaseForMode(false)

    private fun alarmIntent(context: Context, reminderId: Int, mealId: Int, sourceMode: Int): PendingIntent {
        val intent = Intent(context, FutureReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_FUTURE_ID, mealId)
            putExtra("EXTRA_SOURCE_MODE", sourceMode)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(context: Context, action: String, reminderId: Int, mealId: Int, sourceMode: Int): PendingIntent {
        val intent = Intent(context, FutureReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_FUTURE_ID, mealId)
            putExtra("EXTRA_SOURCE_MODE", sourceMode)
        }
        val requestCode = reminderId * 10 + when (action) {
            ACTION_DISMISS -> 1
            ACTION_SNOOZE -> 2
            else -> 0
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun detailIntent(context: Context, mealId: Int, sourceMode: Int): PendingIntent {
        val intent = Intent(context, FutureRecetteDetailActivity::class.java).apply {
            putExtra("FUTURE_ID", mealId)
            putExtra("SOURCE_MODE", sourceMode)
        }
        return PendingIntent.getActivity(
            context,
            mealId * 31 + sourceMode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun scheduleReminder(context: Context, reminder: FutureReminderStore.ReminderEntry) {
        val db = reminderDb(context)
        val saved = FutureReminderStore.loadById(db, reminder.id) ?: return
        if (!saved.enabled) return
        if (saved.triggerAtMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmIntent(context, saved.id, saved.mealId, saved.sourceMode)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, saved.triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, saved.triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, saved.triggerAtMillis, pendingIntent)
        }
    }

    fun scheduleForMeal(context: Context, mealId: Int, sourceMode: Int) {
        val db = reminderDb(context)
        val entries = FutureReminderStore.loadForMeal(db, mealId, sourceMode)
        entries.forEach { scheduleReminder(context, it) }
    }

    fun rescheduleAll(context: Context) {
        val db = reminderDb(context)
        FutureReminderStore.loadAllEnabled(db).forEach { scheduleReminder(context, it) }
    }

    fun cancelReminder(context: Context, reminderId: Int) {
        val db = reminderDb(context)
        val reminder = FutureReminderStore.loadById(db, reminderId)
        val mealId = reminder?.mealId ?: 0
        val sourceMode = reminder?.sourceMode ?: 0
        val intent = Intent(context, FutureReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_FUTURE_ID, mealId)
            putExtra("EXTRA_SOURCE_MODE", sourceMode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        NotificationManagerCompat.from(context).cancel(reminderId)
    }

    fun cancelMealReminders(context: Context, mealId: Int, sourceMode: Int, deleteRows: Boolean) {
        val db = reminderDb(context)
        val reminders = FutureReminderStore.loadForMeal(db, mealId, sourceMode)
        reminders.forEach { cancelReminder(context, it.id) }
        if (deleteRows) {
            FutureReminderStore.deleteForMeal(db, mealId, sourceMode)
        }
    }

    fun snoozeReminder(context: Context, reminderId: Int) {
        val db = reminderDb(context)
        val reminder = FutureReminderStore.loadById(db, reminderId) ?: return
        val snoozedTime = System.currentTimeMillis() + SNOOZE_DELAY_MS
        FutureReminderStore.updateReminderTime(db, reminderId, snoozedTime)
        scheduleReminder(context, reminder.copy(triggerAtMillis = snoozedTime))
    }

    fun dismissReminder(context: Context, reminderId: Int) {
        cancelReminder(context, reminderId)
        val db = reminderDb(context)
        FutureReminderStore.deleteReminder(db, reminderId)
    }

    fun showNotification(context: Context, reminderId: Int) {
        ensureChannel(context)
        if (!SettingsStore.areReminderNotificationsEnabled(context)) return
        val db = reminderDb(context)
        val reminder = FutureReminderStore.loadById(db, reminderId) ?: return
        if (!reminder.enabled) return

        val payload = FutureReminderFormatter.buildPayload(
            context,
            reminder.sourceMode,
            reminder.mealId,
            reminder.triggerAtMillis
        ) ?: run {
            dismissReminder(context, reminderId)
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(payload.title)
            .setContentText(payload.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(detailIntent(context, reminder.mealId, reminder.sourceMode))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.future_reminder_ignore),
                actionIntent(context, ACTION_DISMISS, reminder.id, reminder.mealId, reminder.sourceMode)
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                context.getString(R.string.future_reminder_snooze_1h),
                actionIntent(context, ACTION_SNOOZE, reminder.id, reminder.mealId, reminder.sourceMode)
            )
            .build()

        NotificationManagerCompat.from(context).notify(reminder.id, notification)
    }
}
