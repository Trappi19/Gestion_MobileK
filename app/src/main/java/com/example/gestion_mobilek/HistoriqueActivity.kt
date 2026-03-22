package com.example.gestion_mobilek

import android.app.AlertDialog
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class HistoriqueActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private var selectionMode = false
    private val selectedIds = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historique)

        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerRepas)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnSelect).setOnClickListener {
            if (selectionMode) exitSelectionMode() else enterSelectionMode()
        }

        findViewById<ImageButton>(R.id.btnDeleteSelected).setOnClickListener {
            confirmDeleteSelected()
        }
    }

    override fun onResume() {
        super.onResume()
        exitSelectionMode()
    }

    // ─── MODE SÉLECTION ─────────────────────────────────────────────

    private fun enterSelectionMode() {
        selectionMode = true
        selectedIds.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.VISIBLE
        container.removeAllViews()
        loadRepas()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.GONE
        container.removeAllViews()
        loadRepas()
    }

    private fun confirmDeleteSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Aucun repas sélectionné", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Supprimer ${selectedIds.size} repas ?")
            .setMessage("Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val db = dbHelper.getDatabase()
                    selectedIds.forEach { id ->
                        db.delete("repas", "id = ?", arrayOf(id.toString()))
                    }
                    Toast.makeText(this, "${selectedIds.size} repas supprimé(s)", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ─── LISTE ──────────────────────────────────────────────────────

    private fun loadRepas() {
        try {
            val db = dbHelper.getDatabase()
            // Requête complète avec toutes les colonnes nécessaires
            val cursor = db.rawQuery(
                """SELECT id, nom_plat, id_personnes, nb_jour_repas, description 
                   FROM repas 
                   WHERE nb_jour_repas >= 0 
                   ORDER BY nb_jour_repas ASC""",
                null
            )
            if (cursor.moveToFirst()) {
                do {
                    addRepasRow(
                        cursor.getInt(0),       // id
                        cursor.getString(1) ?: "",  // nom_plat (CSV)
                        cursor.getString(2) ?: "",  // id_personnes (CSV)
                        cursor.getInt(3),       // nb_jour_depuis_repas
                        cursor.getString(4) ?: ""   // description
                    )
                } while (cursor.moveToNext())
            } else {
                val tv = TextView(this)
                tv.text = "Aucun repas dans l'historique"
                tv.textSize = 16f
                tv.gravity = Gravity.CENTER
                tv.setPadding(0, 32, 0, 0)
                container.addView(tv)
            }
            cursor.close()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addRepasRow(
        repasId: Int,
        nomPlat: String,
        idPersonnes: String,
        nbJours: Int,
        description: String
    ) {
        val dateStr = nbJoursToDate(nbJours)

        // Plats : max 2 affichés + "..." si plus
        val platsList = nomPlat.split(",").filter { it.isNotBlank() }
        val platsText = when {
            platsList.isEmpty() -> "Aucun plat"
            platsList.size <= 2 -> platsList.joinToString(", ")
            else -> "${platsList[0]}, ${platsList[1]}..."
        }

        // Personnes : max 2 affichées + "+X" si plus
        val nomsPersonnes = getPersonNames(idPersonnes)
        val noms = nomsPersonnes.split(", ").filter { it.isNotBlank() }
        val personnesText = when {
            noms.isEmpty() -> "Aucune personne"
            noms.size <= 2 -> noms.joinToString(", ")
            else -> "${noms[0]}, ${noms[1]} +${noms.size - 2}"
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8; bottomMargin = 8 }
        row.gravity = Gravity.CENTER_VERTICAL

        // Coche sélection
        val checkBox = CheckBox(this)
        checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedIds.contains(repasId)
        checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedIds.add(repasId) else selectedIds.remove(repasId)
        }

        // Bouton repas
        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        btn.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        btn.textSize = 14f
        btn.text = "🍽️ $platsText\n👥 $personnesText\n📅 $dateStr"

        btn.setOnClickListener {
            if (selectionMode) {
                checkBox.isChecked = !checkBox.isChecked
            } else {
                // Passe TOUTES les infos à RepasDetailActivity
                startActivity(Intent(this, RepasDetailActivity::class.java).apply {
                    putExtra("REPAS_ID", repasId)
                    putExtra("NOM_PLAT", nomPlat)       // CSV complet
                    putExtra("ID_PERSONNES", idPersonnes)
                    putExtra("NB_JOURS", nbJours)
                    putExtra("DESCRIPTION", description)
                })
            }
        }

        row.addView(checkBox)
        row.addView(btn)
        container.addView(row)
    }

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
