package com.example.gestion_mobilek.ui.items

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
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PlatHistoryActivity : AppCompatActivity() {

    private data class MealRow(
        val id: Int,
        val nomPlat: String,
        val idPersonnes: String,
        val dateStorage: String,
        val description: String
    )

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var platName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plat_history)

        dbHelper = DatabaseHelper(this)
        platName = intent.getStringExtra("PLAT_NAME") ?: ""

        if (platName.isBlank()) {
            Toast.makeText(this, "Plat introuvable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<TextView>(R.id.tvTitle).text = platName
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPlanifier)
            .setOnClickListener {
                startActivity(Intent(this, AddEditFutureRecetteActivity::class.java).apply {
                    putExtra("PRESELECTED_PLATS", arrayOf(platName))
                })
            }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        Thread {
            try {
                val db = dbHelper.getDatabase()
                val dateCol = FutureRecettesManager.resolveDateColumn(db)

                // ─── Repas passés ─────────────────────────────────────────
                val pastMeals = mutableListOf<MealRow>()
                val cPast = db.rawQuery(
                    "SELECT id, nom_plat, id_personnes, date_dernier_repas, description FROM repas WHERE nom_plat LIKE ? ORDER BY date_dernier_repas DESC",
                    arrayOf("%$platName%")
                )
                if (cPast.moveToFirst()) {
                    do {
                        pastMeals.add(
                            MealRow(
                                id = cPast.getInt(0),
                                nomPlat = cPast.getString(1) ?: "",
                                idPersonnes = cPast.getString(2) ?: "",
                                dateStorage = DateStorageUtils.normalizeStorageDate(cPast.getString(3)) ?: "",
                                description = cPast.getString(4) ?: ""
                            )
                        )
                    } while (cPast.moveToNext())
                }
                cPast.close()

                // Tri par date décroissante (format YYYYMMDD pour comparaison)
                val pastSorted = pastMeals.sortedByDescending {
                    DateStorageUtils.toSortable(it.dateStorage) ?: ""
                }

                // ─── Statistiques ─────────────────────────────────────────
                val totalCount = pastSorted.size
                val lastDate = pastSorted.firstOrNull()?.dateStorage
                val personCount = mutableMapOf<Int, Int>() // id → nb fois
                pastSorted.forEach { meal ->
                    meal.idPersonnes.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .forEach { pid -> personCount[pid] = (personCount[pid] ?: 0) + 1 }
                }
                val top3Ids = personCount.entries.sortedByDescending { it.value }.take(3).map { it.key }
                val top3Names = top3Ids.mapNotNull { pid ->
                    val c = db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(pid.toString()))
                    val name = if (c.moveToFirst()) c.getString(0) else null
                    c.close()
                    name
                }

                // ─── Repas futurs ─────────────────────────────────────────
                val futureMeals = mutableListOf<MealRow>()
                val cFuture = db.rawQuery(
                    "SELECT id, nom_plat, id_personnes, $dateCol, description FROM future_repas WHERE nom_plat LIKE ?",
                    arrayOf("%$platName%")
                )
                if (cFuture.moveToFirst()) {
                    do {
                        futureMeals.add(
                            MealRow(
                                id = cFuture.getInt(0),
                                nomPlat = cFuture.getString(1) ?: "",
                                idPersonnes = cFuture.getString(2) ?: "",
                                dateStorage = DateStorageUtils.normalizeStorageDate(cFuture.getString(3)) ?: "",
                                description = cFuture.getString(4) ?: ""
                            )
                        )
                    } while (cFuture.moveToNext())
                }
                cFuture.close()

                val futureSorted = futureMeals.sortedBy {
                    DateStorageUtils.toSortable(it.dateStorage) ?: ""
                }

                runOnUiThread {
                    // Stats
                    findViewById<TextView>(R.id.tvStatCount).text = totalCount.toString()
                    findViewById<TextView>(R.id.tvStatLastDate).text =
                        if (lastDate.isNullOrBlank()) "-"
                        else DateStorageUtils.displayFromStorage(lastDate)
                    findViewById<TextView>(R.id.tvStatTopPersons).text =
                        if (top3Names.isEmpty()) "-" else top3Names.joinToString(", ")

                    // Repas passés
                    val containerPast = findViewById<LinearLayout>(R.id.containerPast)
                    containerPast.removeAllViews()
                    if (pastSorted.isEmpty()) {
                        addEmptyRow(containerPast, "Aucun repas passé avec ce plat")
                    } else {
                        pastSorted.forEach { meal ->
                            addMealCard(containerPast, meal, isFuture = false, db = db)
                        }
                    }

                    // Repas futurs
                    val containerFuture = findViewById<LinearLayout>(R.id.containerFuture)
                    containerFuture.removeAllViews()
                    if (futureSorted.isEmpty()) {
                        addEmptyRow(containerFuture, "Aucun repas futur prévu avec ce plat")
                    } else {
                        futureSorted.forEach { meal ->
                            addMealCard(containerFuture, meal, isFuture = true, db = db)
                        }
                    }
                }

            } catch (e: SQLiteException) {
                runOnUiThread {
                    Toast.makeText(this, "Erreur BDD : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun addEmptyRow(container: LinearLayout, msg: String) {
        val tv = TextView(this)
        tv.text = msg
        tv.setTextColor(Color.parseColor("#AAAAAA"))
        tv.setPadding(8, 12, 8, 12)
        tv.textSize = 14f
        container.addView(tv)
    }

    private fun addMealCard(
        container: LinearLayout,
        meal: MealRow,
        isFuture: Boolean,
        db: android.database.sqlite.SQLiteDatabase
    ) {
        val card = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6; bottomMargin = 6 }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f
                setColor(if (isFuture) Color.parseColor("#F0EEFF") else Color.WHITE)
                setStroke(1, Color.parseColor(if (isFuture) "#C8C0FF" else "#E0E0E0"))
            }
        }

        // Date
        val tvDate = TextView(this).apply {
            text = "📅 ${DateStorageUtils.displayFromStorage(meal.dateStorage)}"
            setTextColor(if (isFuture) Color.parseColor("#7B68EE") else Color.parseColor("#4A90E2"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        inner.addView(tvDate)

        // Personnes
        val personNames = resolvePersonNames(meal.idPersonnes, db)
        val tvPersons = TextView(this).apply {
            text = "👥 $personNames"
            setTextColor(Color.parseColor("#444444"))
            textSize = 13f
        }
        inner.addView(tvPersons)

        // Description si non vide
        if (meal.description.isNotBlank()) {
            val tvDesc = TextView(this).apply {
                text = meal.description
                setTextColor(Color.parseColor("#666666"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }
            inner.addView(tvDesc)
        }

        card.addView(inner)
        container.addView(card)

        // Navigation au détail
        card.setOnClickListener {
            if (isFuture) {
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

    private fun resolvePersonNames(idPersonnes: String, db: android.database.sqlite.SQLiteDatabase): String {
        if (idPersonnes.isBlank()) return "Aucune personne"
        val ids = idPersonnes.split(",").filter { it.isNotBlank() }
        val names = mutableListOf<String>()
        ids.forEach { id ->
            val c = db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(id.trim()))
            if (c.moveToFirst()) names.add(c.getString(0) ?: "?")
            c.close()
        }
        return if (names.isEmpty()) "Aucune personne" else names.joinToString(", ")
    }
}
