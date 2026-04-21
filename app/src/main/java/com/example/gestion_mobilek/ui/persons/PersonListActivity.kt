package com.example.gestion_mobilek.ui.persons

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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PersonListActivity : AppCompatActivity() {

    private enum class SelectionMode {
        NONE,
        DELETE,
        GROUP
    }

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var fabOpen = false
    private var selectionMode = SelectionMode.NONE
    private val selectedIds = mutableSetOf<Int>()
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_list)

        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerPersons)
        swipeRefresh = findViewById(R.id.swipeRefreshPersons)
        swipeRefresh.setOnRefreshListener {
            reloadPersons()
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnDeleteSelected).setOnClickListener {
            confirmDeleteSelected()
        }

        findViewById<ImageButton>(R.id.btnSelectionActions).setOnClickListener {
            showGroupActionsMenu(it)
        }

        findViewById<EditText>(R.id.etSearchPersons).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty().trim()
                reloadPersons()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        setupFab()
        updateHeaderForSelectionMode()
    }

    override fun onResume() {
        super.onResume()
        exitSelectionMode()
        reloadPersons()
    }

    // ─── FAB ────────────────────────────────────────────────────────────────

    private fun setupFab() {
        val fabMain = findViewById<FloatingActionButton>(R.id.fabMain)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val fabDelete = findViewById<FloatingActionButton>(R.id.fabDelete)
        val fabSelectGroup = findViewById<FloatingActionButton>(R.id.fabSelectGroup)

        fabMain.setOnClickListener {
            if (fabOpen) closeFab() else openFab()
        }

        fabAdd.setOnClickListener {
            closeFab()
            startActivity(Intent(this, AddPersonActivity::class.java))
        }

        fabDelete.setOnClickListener {
            closeFab()
            enterDeleteSelectionMode()
        }

        fabSelectGroup.setOnClickListener {
            closeFab()
            enterGroupSelectionMode()
        }
    }

    private fun openFab() {
        fabOpen = true
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val fabDelete = findViewById<FloatingActionButton>(R.id.fabDelete)
        val fabSelectGroup = findViewById<FloatingActionButton>(R.id.fabSelectGroup)

        listOf(fabAdd, fabDelete, fabSelectGroup).forEachIndexed { index, fab ->
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
        val fabSelectGroup = findViewById<FloatingActionButton>(R.id.fabSelectGroup)

        listOf(fabAdd, fabDelete, fabSelectGroup).forEach { fab ->
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

    private fun enterDeleteSelectionMode(initialSelectedPersonId: Int? = null) {
        selectionMode = SelectionMode.DELETE
        selectedIds.clear()
        initialSelectedPersonId?.let { selectedIds.add(it) }
        updateHeaderForSelectionMode()
        reloadPersons()
    }

    private fun enterGroupSelectionMode(initialSelectedPersonId: Int? = null) {
        selectionMode = SelectionMode.GROUP
        selectedIds.clear()
        initialSelectedPersonId?.let { selectedIds.add(it) }
        updateHeaderForSelectionMode()
        reloadPersons()
    }

    private fun exitSelectionMode() {
        selectionMode = SelectionMode.NONE
        selectedIds.clear()
        updateHeaderForSelectionMode()
    }

    private fun updateHeaderForSelectionMode() {
        val btnDelete = findViewById<ImageButton>(R.id.btnDeleteSelected)
        val btnGroupActions = findViewById<ImageButton>(R.id.btnSelectionActions)
        val spacer = findViewById<View>(R.id.spacerHeader)

        btnDelete.visibility = if (selectionMode == SelectionMode.DELETE) View.VISIBLE else View.GONE
        btnGroupActions.visibility = if (selectionMode == SelectionMode.GROUP) View.VISIBLE else View.GONE
        spacer.visibility = if (selectionMode == SelectionMode.NONE) View.VISIBLE else View.GONE
    }

    private fun isAnySelectionMode(): Boolean = selectionMode != SelectionMode.NONE

    private fun showGroupActionsMenu(anchor: View) {
        if (selectionMode != SelectionMode.GROUP) return
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Selectionnez au moins une personne", Toast.LENGTH_SHORT).show()
            return
        }

        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 1, "Ajouter a une future recette")
        popup.menu.add(0, 2, 2, "Voir les gouts ensembles")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openAddFutureRecipeWithSelection()
                2 -> openGroupTastePage()
            }
            true
        }
        popup.show()
    }

    private fun openAddFutureRecipeWithSelection() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Selectionnez au moins une personne", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, AddEditFutureRecetteActivity::class.java).apply {
            putExtra("PRESELECTED_PERSON_IDS", selectedIds.toIntArray())
        })
    }

    private fun openGroupTastePage() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Selectionnez au moins une personne", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, PersonGroupTasteActivity::class.java).apply {
            putExtra("SELECTED_PERSON_IDS", selectedIds.toIntArray())
        })
    }

    private fun confirmDeleteSelected() {
        if (selectionMode != SelectionMode.DELETE) {
            Toast.makeText(this, "Mode suppression non actif", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Aucune personne selectionnee", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Supprimer ${selectedIds.size} personne(s) ?")
            .setMessage("Cette action est irreversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val db = dbHelper.getDatabase()
                    selectedIds.forEach { id ->
                        db.delete("personnes", "id = ?", arrayOf(id.toString()))
                        db.delete("gouts", "id_personne = ?", arrayOf(id.toString()))
                    }
                    Toast.makeText(this, "${selectedIds.size} personne(s) supprimee(s)", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                    reloadPersons()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ─── LISTE ──────────────────────────────────────────────────────────────

    private fun reloadPersons() {
        container.removeAllViews()
        loadPersonsFromDb()
    }

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
        } finally {
            swipeRefresh.isRefreshing = false
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

        val checkBox = CheckBox(this)
        checkBox.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        checkBox.visibility = if (isAnySelectionMode()) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedIds.contains(personId)
        checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedIds.add(personId) else selectedIds.remove(personId)
        }

        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
        btn.text = "$name     |     $dateStr"

        btn.setOnClickListener {
            if (isAnySelectionMode()) {
                checkBox.isChecked = !checkBox.isChecked
            } else {
                startActivity(Intent(this, PersonDetailActivity::class.java).apply {
                    putExtra("PERSON_ID", personId)
                    putExtra("PERSON_NAME", name)
                })
            }
        }

        btn.setOnLongClickListener {
            if (!isAnySelectionMode()) showLongPressMenu(personId, name, btn)
            true
        }

        row.addView(checkBox)
        row.addView(btn)
        container.addView(row)
    }

    // ─── LONG PRESS MENU ────────────────────────────────────────────────────

    private fun showLongPressMenu(personId: Int, name: String, anchor: View) {
        val popup = PopupMenu(this, anchor, Gravity.START)

        val title = SpannableString(name).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        popup.menu.add(0, 0, 0, title).apply { isEnabled = false }
        popup.menu.add(0, 1, 1, "Modifier")
        popup.menu.add(0, 2, 2, "Supprimer")
        popup.menu.add(0, 3, 3, "Selectionner")

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
                3 -> enterGroupSelectionMode(personId)
            }
            true
        }
        popup.show()
    }

    private fun confirmDeleteOne(personId: Int, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer $name ?")
            .setMessage("Cette action est irreversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val db = dbHelper.getDatabase()
                    db.delete("personnes", "id = ?", arrayOf(personId.toString()))
                    db.delete("gouts", "id_personne = ?", arrayOf(personId.toString()))
                    Toast.makeText(this, "$name supprime", Toast.LENGTH_SHORT).show()
                    reloadPersons()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
