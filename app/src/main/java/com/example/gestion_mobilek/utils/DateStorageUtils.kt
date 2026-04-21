package com.example.gestion_mobilek.utils

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

import java.util.Calendar
import java.util.Locale

object DateStorageUtils {

    // Stockage attendu en BDD: ddMMyyyy (8 chiffres)
    fun toStorageDate(day: Int, monthZeroBased: Int, year: Int): String {
        return String.format(Locale.US, "%02d%02d%04d", day, monthZeroBased + 1, year)
    }

    fun todayStorageDate(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        return toStorageDate(day, month, year)
    }

    fun displayFromStorage(rawValue: String?): String {
        val normalized = normalizeStorageDate(rawValue) ?: return "Date inconnue"
        val day = normalized.substring(0, 2).toIntOrNull() ?: return "Date inconnue"
        val month = normalized.substring(2, 4).toIntOrNull() ?: return "Date inconnue"
        val year = normalized.substring(4, 8).toIntOrNull() ?: return "Date inconnue"
        return "$day/$month/$year"
    }

    fun normalizeStorageDate(rawValue: String?): String? {
        if (rawValue.isNullOrBlank()) return null
        val digits = rawValue.filter { it.isDigit() }

        // Nouveau format: ddMMyyyy
        if (digits.length == 8) {
            val day = digits.substring(0, 2).toIntOrNull() ?: return null
            val month = digits.substring(2, 4).toIntOrNull() ?: return null
            val year = digits.substring(4, 8).toIntOrNull() ?: return null
            if (!isValidDate(day, month, year)) return null
            return digits
        }

        // Compat temporaire: ancienne valeur = nombre de jours depuis aujourd'hui
        val legacyDays = digits.toIntOrNull() ?: return null
        return fromDaysAgo(legacyDays)
    }

    fun toSortable(rawValue: String?): String? {
        val normalized = normalizeStorageDate(rawValue) ?: return null
        val day = normalized.substring(0, 2)
        val month = normalized.substring(2, 4)
        val year = normalized.substring(4, 8)
        return "$year$month$day"
    }

    fun toLegacyDaysAgo(rawValue: String?): Int? {
        val normalized = normalizeStorageDate(rawValue) ?: return null
        val day = normalized.substring(0, 2).toIntOrNull() ?: return null
        val month = normalized.substring(2, 4).toIntOrNull() ?: return null
        val year = normalized.substring(4, 8).toIntOrNull() ?: return null

        val selected = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMs = today.timeInMillis - selected.timeInMillis
        return (diffMs / 86400000L).toInt()
    }

    fun relativeLabel(rawValue: String?): String? {
        val sortable = toSortable(rawValue) ?: return null
        val todaySortable = toSortable(todayStorageDate()) ?: return null
        if (sortable == todaySortable) return "Aujourd'hui"

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStorage = toStorageDate(
            tomorrow.get(Calendar.DAY_OF_MONTH),
            tomorrow.get(Calendar.MONTH),
            tomorrow.get(Calendar.YEAR)
        )
        val tomorrowSortable = toSortable(tomorrowStorage) ?: return null
        if (sortable == tomorrowSortable) return "Demain"
        return null
    }

    private fun fromDaysAgo(daysAgo: Int): String? {
        if (daysAgo < 0) return null
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        return String.format(Locale.US, "%02d%02d%04d", day, month, year)
    }

    private fun isValidDate(day: Int, month: Int, year: Int): Boolean {
        if (month !in 1..12 || day !in 1..31 || year !in 1900..3000) return false
        return try {
            val cal = Calendar.getInstance().apply {
                isLenient = false
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
            }
            cal.time
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

