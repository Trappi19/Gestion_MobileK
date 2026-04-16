package com.example.gestion_mobilek

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class AddHistoriqueRepasActivity : AppCompatActivity() {

    private data class PersonOption(val id: Int, val name: String)

    private lateinit var dbHelper: DatabaseHelper
    private var selectedDateStorage: String? = null
    private val selectedPersonIds = mutableSetOf<Int>()
    private val selectedPlats = mutableSetOf<String>()

    companion object {
        private const val REQUEST_ADD_PERSON = 6001
        private const val REQUEST_ADD_PLAT = 6002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_historique_repas)

        dbHelper = DatabaseHelper(this)

        val tvDate = findViewById<TextView>(R.id.tvSelectedDateHist)

        findViewById<ImageButton>(R.id.btnBackAddHist).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnPickDateHist).setOnClickListener { showDatePicker(tvDate) }
        findViewById<Button>(R.id.btnPickPersonsHist).setOnClickListener { showPersonsPicker() }
        findViewById<Button>(R.id.btnPickPlatsHist).setOnClickListener { showPlatsPicker() }
        findViewById<Button>(R.id.btnConfirmHist).setOnClickListener { savePastRepas() }
    }

    private fun showDatePicker(tvDate: TextView) {
        val initial = Calendar.getInstance()
        selectedDateStorage?.let { raw ->
            val normalized = DateStorageUtils.normalizeStorageDate(raw)
            if (!normalized.isNullOrBlank()) {
                val day = normalized.substring(0, 2).toIntOrNull() ?: initial.get(Calendar.DAY_OF_MONTH)
                val month = (normalized.substring(2, 4).toIntOrNull() ?: (initial.get(Calendar.MONTH) + 1)) - 1
                val year = normalized.substring(4, 8).toIntOrNull() ?: initial.get(Calendar.YEAR)
                initial.set(year, month, day)
            }
        }

        DatePickerDialog(
            this,
            { _, y, m, d ->
                selectedDateStorage = DateStorageUtils.toStorageDate(d, m, y)
                tvDate.text = "$d/${m + 1}/$y"
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showPersonsPicker() {
        try {
            val db = dbHelper.getDatabase()
            val options = mutableListOf<PersonOption>()

            db.rawQuery("SELECT id, nom FROM personnes ORDER BY nom", null).use { c ->
                if (c.moveToFirst()) {
                    do {
                        options.add(PersonOption(c.getInt(0), c.getString(1) ?: ""))
                    } while (c.moveToNext())
                }
            }

            if (options.isEmpty()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Personnes presentes")
                    .setMessage("Aucune personne disponible.")
                    .setPositiveButton("+ Nouvelle personne") { _, _ -> launchQuickAddPerson() }
                    .setNegativeButton("Annuler", null)
                    .show()
                return
            }

            val initial = options.filter { selectedPersonIds.contains(it.id) }.toSet()
            SearchableMultiSelectDialog.show(
                context = this,
                title = "Personnes presentes",
                items = options,
                labelOf = { it.name },
                initialSelection = initial,
                neutralButtonText = "+ Nouvelle personne",
                onNeutral = { launchQuickAddPerson() },
                onConfirm = { selected ->
                    selectedPersonIds.clear()
                    selectedPersonIds.addAll(selected.map { it.id })
                    refreshPersonsView()
                }
            )

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPlatsPicker() {
        try {
            val db = dbHelper.getDatabase()
            val plats = mutableListOf<String>()

            db.rawQuery("SELECT nom_plat FROM plats ORDER BY nom_plat", null).use { c ->
                if (c.moveToFirst()) {
                    do {
                        plats.add(c.getString(0) ?: "")
                    } while (c.moveToNext())
                }
            }

            if (plats.isEmpty()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Plats servis")
                    .setMessage("Aucun plat disponible.")
                    .setPositiveButton("+ Nouveau plat") { _, _ -> launchQuickAddPlat() }
                    .setNegativeButton("Annuler", null)
                    .show()
                return
            }

            SearchableMultiSelectDialog.show(
                context = this,
                title = "Plats servis",
                items = plats,
                labelOf = { it },
                initialSelection = selectedPlats.toSet(),
                neutralButtonText = "+ Nouveau plat",
                onNeutral = { launchQuickAddPlat() },
                onConfirm = { selected ->
                    selectedPlats.clear()
                    selectedPlats.addAll(selected)
                    refreshPlatsView()
                }
            )

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshPersonsView() {
        val container = findViewById<LinearLayout>(R.id.containerSelectedPersonsHist)
        container.removeAllViews()

        if (selectedPersonIds.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Aucune personne selectionnee"
            tv.setTextColor(0xFF888888.toInt())
            container.addView(tv)
            return
        }

        val names = mutableListOf<String>()
        try {
            val db = dbHelper.getDatabase()
            selectedPersonIds.forEach { id ->
                val c = db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(id.toString()))
                if (c.moveToFirst()) names.add(c.getString(0) ?: "#${id}")
                c.close()
            }
        } catch (_: SQLiteException) {
        }

        names.sorted().forEach { name ->
            val tv = TextView(this)
            tv.text = "- $name"
            tv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            container.addView(tv)
        }
    }

    private fun refreshPlatsView() {
        val container = findViewById<LinearLayout>(R.id.containerSelectedPlatsHist)
        container.removeAllViews()

        if (selectedPlats.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Aucun plat selectionne"
            tv.setTextColor(0xFF888888.toInt())
            container.addView(tv)
            return
        }

        selectedPlats.sorted().forEach { plat ->
            val tv = TextView(this)
            tv.text = "- $plat"
            tv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            container.addView(tv)
        }
    }

    private fun savePastRepas() {
        val dateStorage = selectedDateStorage
        val description = findViewById<EditText>(R.id.etDescriptionHist).text.toString().trim()

        if (dateStorage.isNullOrBlank()) {
            Toast.makeText(this, "Choisissez une date", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedPersonIds.isEmpty()) {
            Toast.makeText(this, "Selectionnez au moins une personne", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedPlats.isEmpty()) {
            Toast.makeText(this, "Selectionnez au moins un plat", Toast.LENGTH_SHORT).show()
            return
        }

        val dateSort = DateStorageUtils.toSortable(dateStorage)
        val todaySort = DateStorageUtils.toSortable(DateStorageUtils.todayStorageDate())
        if (dateSort != null && todaySort != null && dateSort >= todaySort) {
            Toast.makeText(this, "La date doit etre strictement dans le passe", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val db = dbHelper.getDatabase()
            val dateConfig = RepasDateCompat.resolve(db)
            val values = ContentValues().apply {
                put("nom_plat", selectedPlats.joinToString(","))
                put("id_personnes", selectedPersonIds.joinToString(","))
                if (dateConfig.isStorageDate) {
                    put(dateConfig.columnName, dateStorage)
                } else {
                    put(dateConfig.columnName, DateStorageUtils.toLegacyDaysAgo(dateStorage) ?: 0)
                }
                put("description", description)
            }

            db.insert("repas", null, values)
            // Mettre à jour dernier_passage pour les personnes
            selectedPersonIds.forEach { personId ->
                // Vérifier la date existante
                val personCursor = db.rawQuery(
                    "SELECT dernier_passage FROM personnes WHERE id = ?",
                    arrayOf(personId.toString())
                )
                var shouldUpdate = true
                if (personCursor.moveToFirst()) {
                    val existingDate = personCursor.getString(0)
                    if (!existingDate.isNullOrBlank()) {
                        // Comparer les dates au format sortable (yyyyMMdd)
                        val mealSort = DateStorageUtils.toSortable(dateStorage) ?: ""
                        val existingSort = if (dateConfig.isStorageDate) {
                            DateStorageUtils.toSortable(existingDate) ?: ""
                        } else {
                            val asStorage = DateStorageUtils.normalizeStorageDate(existingDate.toIntOrNull()?.toString() ?: "")
                            DateStorageUtils.toSortable(asStorage) ?: ""
                        }
                        // Ne mettre à jour que si mealDate > existingDate
                        shouldUpdate = mealSort > existingSort
                    }
                }
                personCursor.close()

                if (shouldUpdate) {
                    val updateValues = ContentValues().apply {
                        if (dateConfig.isStorageDate) {
                            put("dernier_passage", dateStorage)
                        } else {
                            put("dernier_passage", DateStorageUtils.toLegacyDaysAgo(dateStorage) ?: 0)
                        }
                    }
                    db.update("personnes", updateValues, "id = ?", arrayOf(personId.toString()))
                }
            }
            Toast.makeText(this, "Repas ajoute a l'historique", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchQuickAddPerson() {
        startActivityForResult(Intent(this, AddPersonActivity::class.java), REQUEST_ADD_PERSON)
    }

    private fun launchQuickAddPlat() {
        startActivityForResult(Intent(this, AddItemActivity::class.java).apply {
            putExtra("TYPE", "plat")
        }, REQUEST_ADD_PLAT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQUEST_ADD_PERSON -> {
                val createdPersonId = data?.getIntExtra("PERSON_ID", -1) ?: -1
                if (createdPersonId > 0) {
                    selectedPersonIds.add(createdPersonId)
                    refreshPersonsView()
                }
                showPersonsPicker()
            }

            REQUEST_ADD_PLAT -> {
                val createdPlatName = data?.getStringExtra("ITEM_NAME")?.trim().orEmpty()
                if (createdPlatName.isNotBlank()) {
                    selectedPlats.add(createdPlatName)
                    refreshPlatsView()
                }
                showPlatsPicker()
            }
        }
    }
}

