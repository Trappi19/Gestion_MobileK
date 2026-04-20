package com.example.gestion_mobilek

import android.app.AlertDialog
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class HistoriqueActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private var selectionMode = false
    private val selectedIds = mutableSetOf<Int>()
    private var searchQuery = ""

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

        findViewById<ImageButton>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddHistoriqueRepasActivity::class.java))
        }

        findViewById<EditText>(R.id.etSearchHistorique).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty().trim().lowercase()
                exitSelectionMode()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        try {
            FutureRecettesManager.migrateDueFutureRepas(this, dbHelper.getDatabase())
        } catch (_: SQLiteException) {
        }
        exitSelectionMode()
    }

    // ─── MODE SÉLECTION ─────────────────────────────────────────────

    private fun enterSelectionMode() {
        selectionMode = true
        selectedIds.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.btnAdd).visibility = View.GONE
        container.removeAllViews()
        loadRepas()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.GONE
        findViewById<ImageButton>(R.id.btnAdd).visibility = View.VISIBLE
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
            val dateConfig = RepasDateCompat.resolve(db)
            val cursor = if (dateConfig.isStorageDate) {
                // Convertir la date d'aujourd'hui au format sortable (yyyyMMdd)
                val todayStorage = DateStorageUtils.todayStorageDate()
                val todaySortable = DateStorageUtils.toSortable(todayStorage) ?: "20260416"

                // Expression SQL pour convertir ddMMyyyy en yyyyMMdd
                val orderExpr = "SUBSTR(${dateConfig.columnName}, 5) || SUBSTR(${dateConfig.columnName}, 3, 2) || SUBSTR(${dateConfig.columnName}, 1, 2)"

                db.rawQuery(
                    """SELECT id, nom_plat, id_personnes, ${dateConfig.columnName}, description
                       FROM repas
                       WHERE ${dateConfig.columnName} IS NOT NULL AND TRIM(${dateConfig.columnName}) != ''
                         AND $orderExpr < ?
                       ORDER BY $orderExpr DESC""",
                    arrayOf(todaySortable)
                )
            } else {
                db.rawQuery(
                    """SELECT id, nom_plat, id_personnes, ${dateConfig.columnName}, description
                       FROM repas
                       WHERE ${dateConfig.columnName} > 0
                       ORDER BY ${dateConfig.columnName} ASC""",
                    null
                )
            }
            if (cursor.moveToFirst()) {
                var renderedCount = 0
                do {
                    val before = container.childCount
                    addRepasRow(
                        cursor.getInt(0),       // id
                        cursor.getString(1) ?: "",  // nom_plat (CSV)
                        cursor.getString(2) ?: "",  // id_personnes (CSV)
                        RepasDateCompat.cursorDateAsStorage(dateConfig, cursor.getString(3)),
                        cursor.getString(4) ?: ""   // description
                    )
                    if (container.childCount > before) renderedCount++
                } while (cursor.moveToNext())

                if (renderedCount == 0) {
                    val tv = TextView(this)
                    tv.text = "Aucun résultat"
                    tv.textSize = 16f
                    tv.gravity = Gravity.CENTER
                    tv.setPadding(0, 32, 0, 0)
                    container.addView(tv)
                }
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
        dateDernierRepas: String?,
        description: String
    ) {
        if (!matchesSearch(nomPlat, idPersonnes, dateDernierRepas, description)) {
            return
        }

        val dateStr = DateStorageUtils.displayFromStorage(dateDernierRepas)

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
                    putExtra("DATE_DERNIER_REPAS", dateDernierRepas)
                    putExtra("DESCRIPTION", description)
                })
            }
        }

        row.addView(checkBox)
        row.addView(btn)
        container.addView(row)
    }

    private fun matchesSearch(
        nomPlat: String,
        idPersonnes: String,
        dateDernierRepas: String?,
        description: String
    ): Boolean {
        if (searchQuery.isBlank()) return true
        val dateText = DateStorageUtils.displayFromStorage(dateDernierRepas).lowercase()
        val peopleNames = getPersonNames(idPersonnes).lowercase()
        val haystack = "${nomPlat.lowercase()} ${description.lowercase()} $peopleNames $dateText"
        return haystack.contains(searchQuery)
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
