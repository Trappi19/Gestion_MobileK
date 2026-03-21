package com.example.gestion_mobilek

import android.app.AlertDialog
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ItemListActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private lateinit var type: String

    private var selectionMode = false
    private val selectedNames = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_list)

        type = intent.getStringExtra("TYPE") ?: "ingredient"
        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerItems)

        val title = if (type == "ingredient") "Ingrédients" else "Plats"
        findViewById<TextView>(R.id.tvTitle).text = title

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddItemActivity::class.java).apply {
                putExtra("TYPE", type)
            })
        }

        // Bouton sélection
        findViewById<ImageButton>(R.id.btnSelect).setOnClickListener {
            if (selectionMode) exitSelectionMode() else enterSelectionMode()
        }

        // Poubelle
        findViewById<ImageButton>(R.id.btnDeleteSelected).setOnClickListener {
            confirmDeleteSelected()
        }
    }

    override fun onResume() {
        super.onResume()
        exitSelectionMode()
        container.removeAllViews()
        loadItems()
    }

    // ─── MODE SÉLECTION ─────────────────────────────────────────────────────

    private fun enterSelectionMode() {
        selectionMode = true
        selectedNames.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.btnAdd).visibility = View.GONE
        container.removeAllViews()
        loadItems()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedNames.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.GONE
        findViewById<ImageButton>(R.id.btnAdd).visibility = View.VISIBLE
        container.removeAllViews()
        loadItems()
    }

    private fun confirmDeleteSelected() {
        if (selectedNames.isEmpty()) {
            Toast.makeText(this, "Aucun élément sélectionné", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Supprimer ${selectedNames.size} élément(s) ?")
            .setMessage("Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val db = dbHelper.getDatabase()
                    val table = if (type == "ingredient") "ingrédient" else "plats"
                    val column = if (type == "ingredient") "nom_ingredient" else "nom_plat"

                    selectedNames.forEach { name ->
                        // Supprime l'item
                        db.delete(table, "$column = ?", arrayOf(name))

                        // Nettoie dans toutes les colonnes gouts concernées
                        if (type == "ingredient") {
                            cleanItemFromGouts(db, name, "aime_ingredient")
                            cleanItemFromGouts(db, name, "aime_pas_ingredient")
                        } else {
                            cleanItemFromGouts(db, name, "aime_plat")
                            cleanItemFromGouts(db, name, "aime_pas_plat")
                        }
                    }

                    Toast.makeText(this, "${selectedNames.size} élément(s) supprimé(s)", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun cleanItemFromGouts(db: android.database.sqlite.SQLiteDatabase, itemName: String, column: String) {
        // Récupère toutes les lignes gouts
        val cursor = db.rawQuery("SELECT id_personne, $column FROM gouts", null)
        if (cursor.moveToFirst()) {
            do {
                val personId = cursor.getInt(0)
                val current = cursor.getString(1) ?: ""

                // Retire l'item de la liste CSV
                val updated = current.split(",")
                    .filter { it.isNotBlank() && it.trim() != itemName }
                    .joinToString(",")

                // Met à jour seulement si ça a changé
                if (updated != current) {
                    val values = android.content.ContentValues()
                    values.put(column, updated)
                    db.update("gouts", values, "id_personne = ?", arrayOf(personId.toString()))
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
    }


    // ─── LISTE ──────────────────────────────────────────────────────────────

    private fun loadItems() {
        try {
            val db = dbHelper.getDatabase()
            val query = if (type == "ingredient")
                "SELECT nom_ingredient FROM ingrédient ORDER BY nom_ingredient"
            else
                "SELECT nom_plat FROM plats ORDER BY nom_plat"

            val cursor = db.rawQuery(query, null)
            if (cursor.moveToFirst()) {
                do { addItemRow(cursor.getString(0)) } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addItemRow(name: String) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 4; bottomMargin = 4 }
        row.gravity = android.view.Gravity.CENTER_VERTICAL

        // Coche
        val checkBox = CheckBox(this)
        checkBox.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedNames.contains(name)
        checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedNames.add(name) else selectedNames.remove(name)
        }

        // Texte
        val tv = TextView(this)
        tv.text = "• $name"
        tv.textSize = 16f
        tv.setPadding(8, 16, 8, 16)
        tv.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )

        // En mode sélection, clic sur le texte coche/décoche
        if (selectionMode) {
            tv.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        }

        row.addView(checkBox)
        row.addView(tv)
        container.addView(row)
    }
}
