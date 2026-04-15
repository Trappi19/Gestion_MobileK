package com.example.gestion_mobilek

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PersonListActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private var fabOpen = false
    private var selectionMode = false
    private val selectedIds = mutableSetOf<Int>()
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_list)

        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerPersons)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnDeleteSelected).setOnClickListener {
            confirmDeleteSelected()
        }

        findViewById<EditText>(R.id.etSearchPersons).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty().trim()
                container.removeAllViews()
                loadPersonsFromDb()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        setupFab()
    }

    override fun onResume() {
        super.onResume()
        exitSelectionMode()
        container.removeAllViews()
        loadPersonsFromDb()
    }

    // ─── FAB ────────────────────────────────────────────────────────────────

    private fun setupFab() {
        val fabMain = findViewById<FloatingActionButton>(R.id.fabMain)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val fabDelete = findViewById<FloatingActionButton>(R.id.fabDelete)

        fabMain.setOnClickListener {
            if (fabOpen) closeFab() else openFab()
        }

        fabAdd.setOnClickListener {
            closeFab()
            startActivity(Intent(this, AddPersonActivity::class.java))
        }

        fabDelete.setOnClickListener {
            closeFab()
            enterSelectionMode()
        }
    }

    private fun openFab() {
        fabOpen = true
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val fabDelete = findViewById<FloatingActionButton>(R.id.fabDelete)

        listOf(fabAdd, fabDelete).forEachIndexed { index, fab ->
            fab.visibility = View.VISIBLE
            fab.alpha = 0f
            fab.scaleX = 0f
            fab.scaleY = 0f
            val delay = index * 60L
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(fab, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(fab, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(fab, "scaleY", 0f, 1f)
                )
                startDelay = delay
                duration = 200
                start()
            }
        }
    }

    private fun closeFab() {
        fabOpen = false
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val fabDelete = findViewById<FloatingActionButton>(R.id.fabDelete)

        listOf(fabAdd, fabDelete).forEach { fab ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(fab, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0f)
                )
                duration = 150
                start()
            }
            fab.postDelayed({ fab.visibility = View.GONE }, 150)
        }
    }

    // ─── MODE SÉLECTION ─────────────────────────────────────────────────────

    private fun enterSelectionMode() {
        selectionMode = true
        selectedIds.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.VISIBLE
        findViewById<View>(R.id.spacerHeader).visibility = View.GONE
        // Recharge avec coches
        container.removeAllViews()
        loadPersonsFromDb()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        findViewById<ImageButton>(R.id.btnDeleteSelected).visibility = View.GONE
        findViewById<View>(R.id.spacerHeader).visibility = View.VISIBLE
    }

    private fun confirmDeleteSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Aucune personne sélectionnée", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Supprimer ${selectedIds.size} personne(s) ?")
            .setMessage("Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val db = dbHelper.getDatabase()
                    selectedIds.forEach { id ->
                        db.delete("personnes", "id = ?", arrayOf(id.toString()))
                        db.delete("gouts", "id_personne = ?", arrayOf(id.toString()))
                    }
                    Toast.makeText(this, "${selectedIds.size} personne(s) supprimée(s)", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                    container.removeAllViews()
                    loadPersonsFromDb()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ─── LISTE ──────────────────────────────────────────────────────────────

    private fun loadPersonsFromDb() {
        try {
            val db = dbHelper.getDatabase()
            val cursor: Cursor = if (searchQuery.isBlank()) {
                db.rawQuery("SELECT id, nom, dernier_passage FROM personnes ORDER BY nom", null)
            } else {
                db.rawQuery(
                    "SELECT id, nom, dernier_passage FROM personnes WHERE LOWER(nom) LIKE ? ORDER BY nom",
                    arrayOf("%${searchQuery.lowercase()}%")
                )
            }
            if (cursor.moveToFirst()) {
                do {
                    addPersonRow(cursor.getInt(0), cursor.getString(1), cursor.getString(2))
                } while (cursor.moveToNext())
            } else {
                val tv = TextView(this)
                tv.text = "Aucune personne"
                tv.gravity = Gravity.CENTER
                tv.setPadding(0, 24, 0, 0)
                container.addView(tv)
            }
            cursor.close()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addPersonRow(personId: Int, name: String, dateStorage: String?) {
        val dateStr = DateStorageUtils.displayFromStorage(dateStorage)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8; bottomMargin = 8 }
        row.gravity = Gravity.CENTER_VERTICAL

        // Coche (visible seulement en mode sélection)
        val checkBox = CheckBox(this)
        checkBox.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedIds.contains(personId)
        checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedIds.add(personId) else selectedIds.remove(personId)
        }

        // Bouton personne
        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
        btn.text = "$name     |     $dateStr"

        btn.setOnClickListener {
            if (selectionMode) {
                checkBox.isChecked = !checkBox.isChecked
            } else {
                startActivity(Intent(this, PersonDetailActivity::class.java).apply {
                    putExtra("PERSON_ID", personId)
                    putExtra("PERSON_NAME", name)
                })
            }
        }

        btn.setOnLongClickListener {
            if (!selectionMode) showLongPressMenu(personId, name, btn)
            true
        }

        row.addView(checkBox)
        row.addView(btn)
        container.addView(row)
    }

    // ─── LONG PRESS MENU ────────────────────────────────────────────────────

    private fun showLongPressMenu(personId: Int, name: String, anchor: View) {
        val popup = PopupMenu(this, anchor, Gravity.START)

        // Nom en "titre" désactivé en haut
        popup.menu.add(0, 0, 0, name).apply { isEnabled = false }
        popup.menu.add(0, 1, 1, "✏️  Modifier")
        popup.menu.add(0, 2, 2, "🗑️  Supprimer")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val intent = Intent(this, EditPersonActivity::class.java).apply {
                        putExtra("PERSON_ID", personId)
                        putExtra("PERSON_NAME", name)
                    }
                    startActivity(intent)
                }
                2 -> confirmDeleteOne(personId, name)
            }
            true
        }
        popup.show()
    }

    private fun confirmDeleteOne(personId: Int, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer $name ?")
            .setMessage("Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val db = dbHelper.getDatabase()
                    db.delete("personnes", "id = ?", arrayOf(personId.toString()))
                    db.delete("gouts", "id_personne = ?", arrayOf(personId.toString()))
                    Toast.makeText(this, "$name supprimé", Toast.LENGTH_SHORT).show()
                    container.removeAllViews()
                    loadPersonsFromDb()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
