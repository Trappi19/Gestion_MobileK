package com.example.gestion_mobilek

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class ItemListActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var type: String

    private var selectionMode = false
    private val selectedNames = mutableSetOf<String>()
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_list)

        type = intent.getStringExtra("TYPE") ?: "ingredient"
        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerItems)
        swipeRefresh = findViewById(R.id.swipeRefreshItems)
        swipeRefresh.setOnRefreshListener {
            reloadItems()
        }

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

        findViewById<EditText>(R.id.etSearchItems).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty().trim()
                reloadItems()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        exitSelectionMode()
        reloadItems()
    }

    // ─── MODE SÉLECTION ─────────────────────────────────────────────────────

    private fun enterSelectionMode() {
        selectionMode = true
        selectedNames.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.btnAdd).visibility = View.GONE
        reloadItems()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedNames.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.GONE
        findViewById<ImageButton>(R.id.btnAdd).visibility = View.VISIBLE
        reloadItems()
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
                        deleteItemEverywhere(db, name)
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

    private fun deleteItemEverywhere(db: android.database.sqlite.SQLiteDatabase, itemName: String) {
        val table = if (type == "ingredient") "ingrédient" else "plats"
        val column = if (type == "ingredient") "nom_ingredient" else "nom_plat"

        db.delete(table, "$column = ?", arrayOf(itemName))

        if (type == "ingredient") {
            cleanItemFromGouts(db, itemName, "aime_ingredient")
            cleanItemFromGouts(db, itemName, "aime_pas_ingredient")
        } else {
            cleanItemFromGouts(db, itemName, "aime_plat")
            cleanItemFromGouts(db, itemName, "aime_pas_plat")
            removeTokenFromCsvColumn(db, "repas", "id", "nom_plat", itemName)
            removeTokenFromCsvColumn(db, "future_repas", "id", "nom_plat", itemName)
        }
    }

    private fun removeTokenFromCsvColumn(
        db: android.database.sqlite.SQLiteDatabase,
        table: String,
        idColumn: String,
        csvColumn: String,
        valueToRemove: String
    ) {
        db.rawQuery("SELECT $idColumn, $csvColumn FROM $table", null).use { cursor ->
            if (!cursor.moveToFirst()) return
            do {
                val rowId = cursor.getInt(0)
                val current = cursor.getString(1) ?: ""
                val updated = current.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != valueToRemove }
                    .joinToString(",")

                if (updated != current) {
                    val values = ContentValues().apply { put(csvColumn, updated) }
                    db.update(table, values, "$idColumn = ?", arrayOf(rowId.toString()))
                }
            } while (cursor.moveToNext())
        }
    }

    private fun replaceTokenInCsvColumn(
        db: android.database.sqlite.SQLiteDatabase,
        table: String,
        idColumn: String,
        csvColumn: String,
        oldValue: String,
        newValue: String
    ) {
        db.rawQuery("SELECT $idColumn, $csvColumn FROM $table", null).use { cursor ->
            if (!cursor.moveToFirst()) return
            do {
                val rowId = cursor.getInt(0)
                val current = cursor.getString(1) ?: ""
                val updated = current.split(",")
                    .map { it.trim() }
                    .map { if (it == oldValue) newValue else it }
                    .filter { it.isNotBlank() }
                    .joinToString(",")

                if (updated != current) {
                    val values = ContentValues().apply { put(csvColumn, updated) }
                    db.update(table, values, "$idColumn = ?", arrayOf(rowId.toString()))
                }
            } while (cursor.moveToNext())
        }
    }

    private fun renameItemEverywhere(db: android.database.sqlite.SQLiteDatabase, oldName: String, newName: String) {
        val table = if (type == "ingredient") "ingrédient" else "plats"
        val column = if (type == "ingredient") "nom_ingredient" else "nom_plat"

        db.update(table, ContentValues().apply { put(column, newName) }, "$column = ?", arrayOf(oldName))

        if (type == "ingredient") {
            replaceTokenInCsvColumn(db, "gouts", "id_personne", "aime_ingredient", oldName, newName)
            replaceTokenInCsvColumn(db, "gouts", "id_personne", "aime_pas_ingredient", oldName, newName)
        } else {
            replaceTokenInCsvColumn(db, "gouts", "id_personne", "aime_plat", oldName, newName)
            replaceTokenInCsvColumn(db, "gouts", "id_personne", "aime_pas_plat", oldName, newName)
            replaceTokenInCsvColumn(db, "repas", "id", "nom_plat", oldName, newName)
            replaceTokenInCsvColumn(db, "future_repas", "id", "nom_plat", oldName, newName)
        }
    }


    // ─── LISTE ──────────────────────────────────────────────────────────────

    private fun reloadItems() {
        container.removeAllViews()
        loadItems()
    }

    private fun loadItems() {
        try {
            val db = dbHelper.getDatabase()
            val query = if (type == "ingredient") {
                if (searchQuery.isBlank()) {
                    "SELECT nom_ingredient FROM ingrédient ORDER BY nom_ingredient"
                } else {
                    "SELECT nom_ingredient FROM ingrédient WHERE LOWER(nom_ingredient) LIKE ? ORDER BY nom_ingredient"
                }
            } else {
                if (searchQuery.isBlank()) {
                    "SELECT nom_plat FROM plats ORDER BY nom_plat"
                } else {
                    "SELECT nom_plat FROM plats WHERE LOWER(nom_plat) LIKE ? ORDER BY nom_plat"
                }
            }

            val args = if (searchQuery.isBlank()) null else arrayOf("%${searchQuery.lowercase()}%")
            val cursor = db.rawQuery(query, args)
            if (cursor.moveToFirst()) {
                do { addItemRow(cursor.getString(0)) } while (cursor.moveToNext())
            } else {
                val tv = TextView(this)
                tv.text = "Aucun élément"
                tv.setPadding(8, 16, 8, 16)
                container.addView(tv)
            }
            cursor.close()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            swipeRefresh.isRefreshing = false
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
        } else {
            tv.setOnLongClickListener {
                showLongPressMenu(name, tv)
                true
            }
        }

        row.addView(checkBox)
        row.addView(tv)
        container.addView(row)
    }

    private fun showLongPressMenu(itemName: String, anchor: View) {
        val popup = PopupMenu(this, anchor)
        val title = SpannableString(itemName).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        popup.menu.add(0, 0, 0, title).apply { isEnabled = false }
        popup.menu.add(0, 1, 1, "Modifier")
        popup.menu.add(0, 2, 2, "Supprimer")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showEditDialog(itemName)
                2 -> confirmDeleteOne(itemName)
            }
            true
        }
        popup.show()
    }

    private fun showEditDialog(oldName: String) {
        val input = EditText(this).apply {
            setText(oldName)
            setSelection(oldName.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Modifier")
            .setView(input)
            .setPositiveButton("Sauvegarder") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) {
                    Toast.makeText(this, "Nom invalide", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == oldName) return@setPositiveButton

                try {
                    val db = dbHelper.getDatabase()
                    db.beginTransaction()
                    try {
                        renameItemEverywhere(db, oldName, newName)
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                    reloadItems()
                    Toast.makeText(this, "Mis à jour", Toast.LENGTH_SHORT).show()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDeleteOne(itemName: String) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer $itemName ?")
            .setMessage("Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val db = dbHelper.getDatabase()
                    deleteItemEverywhere(db, itemName)
                    reloadItems()
                    Toast.makeText(this, "Supprimé", Toast.LENGTH_SHORT).show()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
