package com.example.gestion_mobilek

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.gestion_mobilek.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        setupBottomSheet()

        binding.btnPlats.setOnClickListener {
            startActivity(Intent(this, ItemListActivity::class.java).apply {
                putExtra("TYPE", "plat")
            })
        }
        binding.btnPersonnes.setOnClickListener {
            startActivity(Intent(this, PersonListActivity::class.java))
        }
        binding.btnIngredients.setOnClickListener {
            startActivity(Intent(this, ItemListActivity::class.java).apply {
                putExtra("TYPE", "ingredient")
            })
        }
        binding.btnFutureRecettes.setOnClickListener {
            Toast.makeText(this, "Future Recettes à venir !", Toast.LENGTH_SHORT).show()
        }
        binding.btnHistorique.setOnClickListener {
            startActivity(Intent(this, HistoriqueActivity::class.java))
        }
        binding.btnMenu.setOnClickListener {
            Toast.makeText(this, "Menu à implémenter", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatsFromDB()
        binding.containerLastMeals.post {
            loadLastMeals()
        }
    }

    // ─── BOTTOM SHEET ───────────────────────────────────────────────

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.peekHeight = dpToPx(80)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isHideable = false

        val screenHeight = resources.displayMetrics.heightPixels
        val maxHeight = screenHeight / 2

        binding.bottomSheet.post {
            val params = binding.bottomSheet.layoutParams
            params.height = maxHeight
            binding.bottomSheet.layoutParams = params
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    // ─── STATS ──────────────────────────────────────────────────────

    private fun updateStatsFromDB() {
        try {
            val db = dbHelper.getDatabase()

            val cp = db.rawQuery("SELECT COUNT(*) FROM plats", null)
            cp.moveToFirst()
            binding.tvStatsPlats.text = "Plats: ${cp.getInt(0)}"
            cp.close()

            val ch = db.rawQuery(
                "SELECT COUNT(*) FROM repas WHERE nb_jour_depuis_repas >= 0", null
            )
            ch.moveToFirst()
            binding.tvStatsHistorique.text = "Historique: ${ch.getInt(0)}"
            ch.close()

        } catch (e: SQLiteException) { /* silencieux */ }
    }

    // ─── 3 DERNIERS REPAS ───────────────────────────────────────────

    private fun loadLastMeals() {
        binding.containerLastMeals.removeAllViews()
        try {
            val db = dbHelper.getDatabase()
            val cursor = db.rawQuery(
                """SELECT id, nom_plat, id_personnes, nb_jour_depuis_repas 
                   FROM repas 
                   WHERE nb_jour_depuis_repas >= 0 
                   ORDER BY nb_jour_depuis_repas ASC 
                   LIMIT 3""",
                null
            )
            if (cursor.moveToFirst()) {
                do {
                    addLastMealRow(
                        cursor.getInt(0),
                        cursor.getString(1) ?: "",
                        cursor.getString(2) ?: "",
                        cursor.getInt(3)
                    )
                } while (cursor.moveToNext())
            } else {
                val tv = TextView(this)
                tv.text = "Aucun repas passé"
                tv.textSize = 14f
                tv.setTextColor(0xFFAAAAAA.toInt())
                tv.setPadding(0, 8, 0, 8)
                binding.containerLastMeals.addView(tv)
            }
            cursor.close()
        } catch (e: SQLiteException) { /* silencieux */ }
    }

    private fun addLastMealRow(
        repasId: Int,
        nomPlat: String,
        idPersonnes: String,
        nbJours: Int
    ) {
        val dateStr = nbJoursToDate(nbJours)

        // Plats CSV : max 2 affichés
        val platsList = nomPlat.split(",").filter { it.isNotBlank() }
        val platsText = when {
            platsList.isEmpty() -> "Aucun plat"
            platsList.size <= 2 -> platsList.joinToString(", ")
            else -> "${platsList[0]}, ${platsList[1]}..."
        }

        // Personnes : max 2 affichées
        val nomsPersonnes = getPersonNames(idPersonnes)
        val noms = nomsPersonnes.split(", ").filter { it.isNotBlank() }
        val personnesText = when {
            noms.isEmpty() -> "Aucune personne"
            noms.size <= 2 -> noms.joinToString(", ")
            else -> "${noms[0]}, ${noms[1]} +${noms.size - 2}"
        }

        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 4; bottomMargin = 4 }
        btn.textSize = 13f
        btn.textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_START
        btn.text = "🍽️ $platsText\n👥 $personnesText\n📅 $dateStr"
        btn.setTextColor(0xFFFFFFFF.toInt())
        btn.setBackgroundColor(0xFF2A2A2A.toInt())
        btn.setOnClickListener {
            startActivity(Intent(this, HistoriqueActivity::class.java))
        }
        binding.containerLastMeals.addView(btn)
    }

    // ─── UTILITAIRES ────────────────────────────────────────────────

    private fun nbJoursToDate(nbJours: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -nbJours)
        return "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
    }

    private fun getPersonNames(idPersonnes: String): String {
        if (idPersonnes.isBlank()) return ""
        return try {
            val db = dbHelper.getDatabase()
            val ids = idPersonnes.split(",").filter { it.isNotBlank() }
            val noms = mutableListOf<String>()
            ids.forEach { id ->
                val c = db.rawQuery(
                    "SELECT nom FROM personnes WHERE id = ?",
                    arrayOf(id.trim())
                )
                if (c.moveToFirst()) noms.add(c.getString(0))
                c.close()
            }
            noms.joinToString(", ")
        } catch (e: SQLiteException) { "?" }
    }
}
