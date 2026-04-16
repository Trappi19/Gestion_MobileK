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

class FutureRecettesActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private var selectionMode = false
    private val selectedIds = mutableSetOf<Int>()
    private var futureDateColumn: String = FutureRecettesManager.NEW_DATE_COL
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_future_recettes)

        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerFutureRepas)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddEditFutureRecetteActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSelect).setOnClickListener {
            if (selectionMode) exitSelectionMode() else enterSelectionMode()
        }
        findViewById<ImageButton>(R.id.btnDeleteSelected).setOnClickListener {
            confirmDeleteSelected()
        }

        findViewById<EditText>(R.id.etSearchFuture).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty().trim()
                container.removeAllViews()
                loadFutureRepas()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        try {
            val db = dbHelper.getDatabase()
            FutureRecettesManager.ensureSchema(db)
            futureDateColumn = FutureRecettesManager.resolveDateColumn(db)
            FutureRecettesManager.migrateDueFutureRepas(db)
        } catch (_: SQLiteException) {
        }
        exitSelectionMode()
    }

    private fun enterSelectionMode() {
        selectionMode = true
        selectedIds.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.btnAdd).visibility = View.GONE
        container.removeAllViews()
        loadFutureRepas()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.GONE
        findViewById<ImageButton>(R.id.btnAdd).visibility = View.VISIBLE
        container.removeAllViews()
        loadFutureRepas()
    }

    private fun loadFutureRepas() {
        try {
            val db = dbHelper.getDatabase()
            FutureRecettesManager.ensureSchema(db)
            futureDateColumn = FutureRecettesManager.resolveDateColumn(db)

            val todayStorage = DateStorageUtils.todayStorageDate()
            val todaySortable = DateStorageUtils.toSortable(todayStorage) ?: "20260416"
            
            // Expression SQL pour convertir ddMMyyyy en yyyyMMdd
            val orderExpr = "SUBSTR($futureDateColumn, 5) || SUBSTR($futureDateColumn, 3, 2) || SUBSTR($futureDateColumn, 1, 2)"

            val cursor = if (searchQuery.isBlank()) {
                db.rawQuery(
                    """SELECT id, nom_plat, id_personnes, $futureDateColumn, description
                       FROM future_repas
                       WHERE $futureDateColumn IS NOT NULL AND TRIM($futureDateColumn) != ''
                         AND $orderExpr >= ?
                       ORDER BY $orderExpr ASC""",
                    arrayOf(todaySortable)
                )
            } else {
                val likeSearch = "%${searchQuery.lowercase()}%"
                db.rawQuery(
                    """SELECT id, nom_plat, id_personnes, $futureDateColumn, description
                       FROM future_repas
                       WHERE $futureDateColumn IS NOT NULL AND TRIM($futureDateColumn) != ''
                         AND $orderExpr >= ?
                          AND (LOWER(nom_plat) LIKE ? OR LOWER(IFNULL(description, '')) LIKE ?)
                       ORDER BY $orderExpr ASC""",
                    arrayOf(todaySortable, likeSearch, likeSearch)
                )
            }

            if (cursor.moveToFirst()) {
                var lastDateHeader: String? = null
                do {
                    val normalizedDate = DateStorageUtils.normalizeStorageDate(cursor.getString(3))
                    if (normalizedDate != null && normalizedDate != lastDateHeader) {
                        addDateHeader(normalizedDate)
                        lastDateHeader = normalizedDate
                    }
                    addFutureRepasRow(
                        cursor.getInt(0),
                        cursor.getString(1) ?: "",
                        cursor.getString(2) ?: "",
                        normalizedDate
                    )
                } while (cursor.moveToNext())
            } else {
                val tv = TextView(this)
                tv.text = "Aucune recette planifiée"
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

    private fun addFutureRepasRow(
        futureId: Int,
        nomPlat: String,
        idPersonnes: String,
        dateRepas: String?
    ) {
        val dateStr = DateStorageUtils.displayFromStorage(dateRepas)
        val relative = DateStorageUtils.relativeLabel(dateRepas)

        val platsList = nomPlat.split(",").filter { it.isNotBlank() }
        val platsText = when {
            platsList.isEmpty() -> "Aucun plat"
            platsList.size <= 2 -> platsList.joinToString(", ")
            else -> "${platsList[0]}, ${platsList[1]}..."
        }

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

        val checkBox = CheckBox(this)
        checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedIds.contains(futureId)
        checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedIds.add(futureId) else selectedIds.remove(futureId)
        }

        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        btn.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        btn.textSize = 14f
        val dateLine = if (relative != null) "📅 $dateStr   •   $relative" else "📅 $dateStr"
        btn.text = "🍽️ $platsText\n👥 $personnesText\n$dateLine"

        btn.setOnClickListener {
            if (selectionMode) {
                checkBox.isChecked = !checkBox.isChecked
            } else {
                startActivity(Intent(this, FutureRecetteDetailActivity::class.java).apply {
                    putExtra("FUTURE_ID", futureId)
                })
            }
        }

        btn.setOnLongClickListener {
            if (!selectionMode) showLongPressMenu(futureId, platsText, btn)
            true
        }

        row.addView(checkBox)
        row.addView(btn)
        container.addView(row)
    }

    private fun addDateHeader(dateStorage: String) {
        val tv = TextView(this)
        val date = DateStorageUtils.displayFromStorage(dateStorage)
        val relative = DateStorageUtils.relativeLabel(dateStorage)
        tv.text = if (relative != null) "$date - $relative" else date
        tv.textSize = 13f
        tv.setTextColor(0xFF666666.toInt())
        tv.setPadding(0, 10, 0, 6)
        container.addView(tv)
    }

    private fun showLongPressMenu(futureId: Int, label: String, anchor: View) {
        val popup = PopupMenu(this, anchor, Gravity.START)
        popup.menu.add(0, 0, 0, label).apply { isEnabled = false }
        popup.menu.add(0, 1, 1, "✏️  Modifier")
        popup.menu.add(0, 2, 2, "🗑️  Supprimer")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, AddEditFutureRecetteActivity::class.java).apply {
                    putExtra("FUTURE_ID", futureId)
                })
                2 -> confirmDeleteOne(futureId)
            }
            true
        }
        popup.show()
    }

    private fun confirmDeleteOne(futureId: Int) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer cette recette ?")
            .setMessage("Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    dbHelper.getDatabase().delete("future_repas", "id = ?", arrayOf(futureId.toString()))
                    container.removeAllViews()
                    loadFutureRepas()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDeleteSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Aucune recette sélectionnée", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Supprimer ${selectedIds.size} recette(s) ?")
            .setMessage("Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val db = dbHelper.getDatabase()
                    selectedIds.forEach { id ->
                        db.delete("future_repas", "id = ?", arrayOf(id.toString()))
                    }
                    Toast.makeText(this, "${selectedIds.size} recette(s) supprimée(s)", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
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
        } catch (_: SQLiteException) { "?" }
    }
}




