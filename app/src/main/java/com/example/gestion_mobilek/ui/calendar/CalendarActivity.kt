package com.example.gestion_mobilek.ui.calendar

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

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    // ─── Data classes ─────────────────────────────────────────────────────

    private data class MealEntry(
        val id: Int,
        val nomPlat: String,
        val idPersonnes: String,
        val dateStorage: String,
        val isFuture: Boolean
    )

    // ─── ViewContainers pour kizitonwose ──────────────────────────────────

    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val tvDay: TextView = view.findViewById(R.id.tvDay)
        val containerDots: LinearLayout = view.findViewById(R.id.containerDots)
        lateinit var day: CalendarDay
    }

    inner class MonthHeaderContainer(view: View) : ViewContainer(view)

    // ─── State ────────────────────────────────────────────────────────────

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var calendarView: CalendarView
    private lateinit var tvMonthYear: TextView
    private lateinit var tvSelectedDayLabel: TextView
    private lateinit var containerDayMeals: LinearLayout
    private lateinit var btnPlanifierRepas: android.widget.Button

    private var currentMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate? = null

    // Map <"DDMMYYYY" → liste de meals>
    private val mealsByDay = mutableMapOf<String, MutableList<MealEntry>>()

    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

    // ─── onCreate ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        dbHelper = DatabaseHelper(this)
        calendarView = findViewById(R.id.calendarView)
        tvMonthYear = findViewById(R.id.tvMonthYear)
        tvSelectedDayLabel = findViewById(R.id.tvSelectedDayLabel)
        containerDayMeals = findViewById(R.id.containerDayMeals)
        btnPlanifierRepas = findViewById(R.id.btnPlanifierRepas)

        btnPlanifierRepas.setOnClickListener {
            val date = selectedDate ?: LocalDate.now()
            val intent = Intent(this, AddEditFutureRecetteActivity::class.java)
            intent.putExtra("PRESELECTED_DATE", storageKeyFromLocalDate(date))
            startActivity(intent)
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnPrevMonth).setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            calendarView.smoothScrollToMonth(currentMonth)
        }

        findViewById<ImageButton>(R.id.btnNextMonth).setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            calendarView.smoothScrollToMonth(currentMonth)
        }

        findViewById<Button>(R.id.btnToday).setOnClickListener {
            currentMonth = YearMonth.now()
            calendarView.smoothScrollToMonth(currentMonth)
            selectDate(LocalDate.now())
        }

        setupCalendar()
    }

    override fun onResume() {
        super.onResume()
        loadMealsForMonth(currentMonth)
        calendarView.notifyCalendarChanged()
        selectedDate?.let { showMealsForDate(it) }
    }

    // ─── Setup CalendarView ───────────────────────────────────────────────

    private fun setupCalendar() {
        val startMonth = YearMonth.now().minusMonths(12)
        val endMonth = YearMonth.now().plusMonths(24)

        // Assigner les binders AVANT setup() — obligatoire en v2
        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.day = data
                val tv = container.tvDay
                val dots = container.containerDots
                dots.removeAllViews()

                tv.text = data.date.dayOfMonth.toString()

                if (data.position == DayPosition.MonthDate) {
                    // Couleur texte
                    val today = LocalDate.now()
                    tv.setTextColor(when {
                        data.date == today -> Color.parseColor("#4A90E2")
                        data.date == selectedDate -> Color.WHITE
                        else -> Color.parseColor("#222222")
                    })

                    // Fond sélection
                    if (data.date == selectedDate) {
                        tv.setBackgroundResource(R.drawable.bg_calendar_selected)
                    } else {
                        tv.background = null
                    }

                    // Gras pour aujourd'hui
                    tv.setTypeface(null, if (data.date == today) Typeface.BOLD else Typeface.NORMAL)

                    // Points colorés
                    val key = storageKeyFromLocalDate(data.date)
                    val meals = mealsByDay[key]
                    if (!meals.isNullOrEmpty()) {
                        val hasFuture = meals.any { it.isFuture }
                        val hasPast = meals.any { !it.isFuture }
                        if (hasFuture) addDot(dots, Color.parseColor("#7B68EE"))
                        if (hasPast) addDot(dots, Color.parseColor("#AAAAAA"))
                    }

                    // Tap simple
                    container.view.setOnClickListener {
                        selectDate(data.date)
                    }

                    // Long press → créer repas futur
                    container.view.setOnLongClickListener {
                        val intent = Intent(this@CalendarActivity, AddEditFutureRecetteActivity::class.java)
                        intent.putExtra("PRESELECTED_DATE", storageKeyFromLocalDate(data.date))
                        startActivity(intent)
                        true
                    }

                } else {
                    // Jours hors mois
                    tv.setTextColor(Color.parseColor("#CCCCCC"))
                    tv.background = null
                    container.view.setOnClickListener(null)
                    container.view.setOnLongClickListener(null)
                }
            }
        }

        calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthHeaderContainer> {
            override fun create(view: View) = MonthHeaderContainer(view)
            override fun bind(container: MonthHeaderContainer, data: CalendarMonth) {}
        }

        // setup() après les binders
        calendarView.setup(startMonth, endMonth, DayOfWeek.MONDAY)
        calendarView.scrollToMonth(currentMonth)

        calendarView.monthScrollListener = { month ->
            currentMonth = month.yearMonth
            tvMonthYear.text = currentMonth.format(monthFormatter)
                .replaceFirstChar { it.uppercase() }
            loadMealsForMonth(currentMonth)
            calendarView.notifyCalendarChanged()
        }

        tvMonthYear.text = currentMonth.format(monthFormatter)
            .replaceFirstChar { it.uppercase() }

        loadMealsForMonth(currentMonth)
    }

    // ─── Sélection d'un jour ──────────────────────────────────────────────

    private fun selectDate(date: LocalDate) {
        val old = selectedDate
        selectedDate = date
        old?.let { calendarView.notifyDateChanged(it) }
        calendarView.notifyDateChanged(date)
        showMealsForDate(date)
    }

    // ─── Chargement données ───────────────────────────────────────────────

    private fun loadMealsForMonth(month: YearMonth) {
        mealsByDay.clear()
        val mm = String.format("%02d", month.monthValue)
        Thread {
            try {
                val db = dbHelper.getDatabase()
                val dateCol = FutureRecettesManager.resolveDateColumn(db)

                // Repas futurs
                val cursorF = db.rawQuery(
                    "SELECT id, nom_plat, id_personnes, $dateCol FROM future_repas WHERE $dateCol LIKE ?",
                    arrayOf("__${mm}____")
                )
                if (cursorF.moveToFirst()) {
                    do {
                        val id = cursorF.getInt(0)
                        val nom = cursorF.getString(1) ?: ""
                        val ids = cursorF.getString(2) ?: ""
                        val rawDate = cursorF.getString(3) ?: ""
                        val key = DateStorageUtils.normalizeStorageDate(rawDate) ?: continue
                        mealsByDay.getOrPut(key) { mutableListOf() }
                            .add(MealEntry(id, nom, ids, key, true))
                    } while (cursorF.moveToNext())
                }
                cursorF.close()

                // Repas passés
                val cursorP = db.rawQuery(
                    "SELECT id, nom_plat, id_personnes, date_dernier_repas FROM repas WHERE date_dernier_repas LIKE ?",
                    arrayOf("__${mm}____")
                )
                if (cursorP.moveToFirst()) {
                    do {
                        val id = cursorP.getInt(0)
                        val nom = cursorP.getString(1) ?: ""
                        val ids = cursorP.getString(2) ?: ""
                        val rawDate = cursorP.getString(3) ?: ""
                        val key = DateStorageUtils.normalizeStorageDate(rawDate) ?: continue
                        mealsByDay.getOrPut(key) { mutableListOf() }
                            .add(MealEntry(id, nom, ids, key, false))
                    } while (cursorP.moveToNext())
                }
                cursorP.close()

            } catch (_: SQLiteException) {}

            runOnUiThread {
                calendarView.notifyCalendarChanged()
                selectedDate?.let { showMealsForDate(it) }
            }
        }.start()
    }

    // ─── Affichage repas du jour sélectionné ─────────────────────────────

    private fun showMealsForDate(date: LocalDate) {
        containerDayMeals.removeAllViews()
        val key = storageKeyFromLocalDate(date)
        val display = DateStorageUtils.displayFromStorage(key)
        tvSelectedDayLabel.text = "Repas du $display"

        val meals = mealsByDay[key]
        if (meals.isNullOrEmpty()) {
            val tv = TextView(this)
            tv.text = "Aucun repas ce jour"
            tv.setTextColor(Color.parseColor("#AAAAAA"))
            tv.setPadding(0, 8, 0, 8)
            containerDayMeals.addView(tv)
            return
        }

        meals.forEach { meal ->
            addMealRow(meal)
        }
    }

    private fun addMealRow(meal: MealEntry) {
        val card = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6; bottomMargin = 6 }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f
                setColor(if (meal.isFuture) Color.parseColor("#F0EEFF") else Color.parseColor("#F8F8F8"))
            }
        }

        // Indicateur couleur
        val indicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(4.dpToPx(), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(if (meal.isFuture) Color.parseColor("#7B68EE") else Color.parseColor("#AAAAAA"))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(indicator)

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12, 0, 0, 0)
        }

        val tvPlats = TextView(this).apply {
            text = if (meal.nomPlat.isBlank()) "Repas" else meal.nomPlat.replace(",", ", ")
            setTextColor(Color.parseColor("#222222"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }

        val tvPersonnes = TextView(this).apply {
            text = resolvePersonNames(meal.idPersonnes)
            setTextColor(Color.parseColor("#666666"))
            textSize = 13f
        }

        val tvBadge = TextView(this).apply {
            text = if (meal.isFuture) "Planifié" else "Passé"
            setTextColor(if (meal.isFuture) Color.parseColor("#7B68EE") else Color.parseColor("#888888"))
            textSize = 11f
        }

        textContainer.addView(tvPlats)
        textContainer.addView(tvPersonnes)
        textContainer.addView(tvBadge)
        row.addView(textContainer)
        inner.addView(row)
        card.addView(inner)
        containerDayMeals.addView(card)

        // Navigation au détail
        card.setOnClickListener {
            if (meal.isFuture) {
                startActivity(Intent(this, FutureRecetteDetailActivity::class.java).apply {
                    putExtra("FUTURE_ID", meal.id)
                })
            } else {
                startActivity(Intent(this, RepasDetailActivity::class.java).apply {
                    putExtra("REPAS_ID", meal.id)
                })
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun addDot(container: LinearLayout, color: Int) {
        val dot = View(this)
        val size = 7.dpToPx()
        val params = LinearLayout.LayoutParams(size, size).apply {
            marginStart = 2; marginEnd = 2
        }
        dot.layoutParams = params
        dot.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
        container.addView(dot)
    }

    private fun storageKeyFromLocalDate(date: LocalDate): String {
        return String.format("%02d%02d%04d", date.dayOfMonth, date.monthValue, date.year)
    }

    private fun resolvePersonNames(idPersonnes: String): String {
        if (idPersonnes.isBlank()) return "Aucune personne"
        return try {
            val db = dbHelper.getDatabase()
            val ids = idPersonnes.split(",").filter { it.isNotBlank() }
            val names = mutableListOf<String>()
            ids.forEach { id ->
                val c = db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(id.trim()))
                if (c.moveToFirst()) names.add(c.getString(0) ?: "?")
                c.close()
            }
            if (names.isEmpty()) "Aucune personne" else names.joinToString(", ")
        } catch (_: SQLiteException) { "?" }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
