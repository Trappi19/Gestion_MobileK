package com.example.gestion_mobilek

import android.app.DatePickerDialog
import android.content.ContentValues
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class AddEditFutureRecetteActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var futureId: Int = -1
    private var selectedDateStorage: String? = null
    private val selectedPersonIds = mutableSetOf<Int>()
    private val selectedPlats = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_future_recette)

        dbHelper = DatabaseHelper(this)
        futureId = intent.getIntExtra("FUTURE_ID", -1)

        try {
            FutureRecettesManager.ensureSchema(dbHelper.getDatabase())
        } catch (_: SQLiteException) {
        }

        val tvTitle = findViewById<TextView>(R.id.tvTitleFuture)
        val tvDate = findViewById<TextView>(R.id.tvSelectedDate)

        findViewById<ImageButton>(R.id.btnBackAdd).setOnClickListener { finish() }

        if (futureId > 0) {
            tvTitle.text = "Modifier recette planifiée"
            findViewById<Button>(R.id.btnConfirmFuture).text = "Sauvegarder"
            loadExistingData(tvDate)
        }

        findViewById<Button>(R.id.btnPickDate).setOnClickListener {
            showDatePicker(tvDate)
        }

        findViewById<Button>(R.id.btnPickPersons).setOnClickListener {
            showPersonsPicker()
        }

        findViewById<Button>(R.id.btnPickPlats).setOnClickListener {
            showPlatsPicker()
        }

        findViewById<Button>(R.id.btnConfirmFuture).setOnClickListener {
            saveFutureRecette()
        }
    }

    private fun loadExistingData(tvDate: TextView) {
        try {
            val db = dbHelper.getDatabase()
            val cursor = db.rawQuery(
                "SELECT nom_plat, id_personnes, date_repas, description FROM future_repas WHERE id = ?",
                arrayOf(futureId.toString())
            )
            if (cursor.moveToFirst()) {
                val nomsPlats = cursor.getString(0) ?: ""
                val idsPersonnes = cursor.getString(1) ?: ""
                selectedDateStorage = DateStorageUtils.normalizeStorageDate(cursor.getString(2))
                val description = cursor.getString(3) ?: ""

                selectedPlats.clear()
                selectedPlats.addAll(nomsPlats.split(",").map { it.trim() }.filter { it.isNotBlank() })

                selectedPersonIds.clear()
                selectedPersonIds.addAll(idsPersonnes.split(",").mapNotNull { it.trim().toIntOrNull() })

                tvDate.text = DateStorageUtils.displayFromStorage(selectedDateStorage)
                findViewById<EditText>(R.id.etDescriptionFuture).setText(description)
                refreshPersonsView()
                refreshPlatsView()
            }
            cursor.close()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur chargement: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
            val ids = mutableListOf<Int>()
            val names = mutableListOf<String>()

            db.rawQuery("SELECT id, nom FROM personnes ORDER BY nom", null).use { c ->
                if (c.moveToFirst()) {
                    do {
                        ids.add(c.getInt(0))
                        names.add(c.getString(1) ?: "")
                    } while (c.moveToNext())
                }
            }

            if (ids.isEmpty()) {
                Toast.makeText(this, "Ajoute d'abord des personnes", Toast.LENGTH_SHORT).show()
                return
            }

            val checked = BooleanArray(ids.size) { selectedPersonIds.contains(ids[it]) }

            android.app.AlertDialog.Builder(this)
                .setTitle("Personnes présentes")
                .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked ->
                    if (isChecked) selectedPersonIds.add(ids[which]) else selectedPersonIds.remove(ids[which])
                }
                .setPositiveButton("OK") { _, _ -> refreshPersonsView() }
                .setNegativeButton("Annuler", null)
                .show()

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
                Toast.makeText(this, "Ajoute d'abord des plats", Toast.LENGTH_SHORT).show()
                return
            }

            val checked = BooleanArray(plats.size) { selectedPlats.contains(plats[it]) }

            android.app.AlertDialog.Builder(this)
                .setTitle("Plats possibles")
                .setMultiChoiceItems(plats.toTypedArray(), checked) { _, which, isChecked ->
                    if (isChecked) selectedPlats.add(plats[which]) else selectedPlats.remove(plats[which])
                }
                .setPositiveButton("OK") { _, _ -> refreshPlatsView() }
                .setNegativeButton("Annuler", null)
                .show()

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshPersonsView() {
        val container = findViewById<LinearLayout>(R.id.containerSelectedPersons)
        container.removeAllViews()

        if (selectedPersonIds.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Aucune personne sélectionnée"
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
            tv.text = "• $name"
            tv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            container.addView(tv)
        }
    }

    private fun refreshPlatsView() {
        val container = findViewById<LinearLayout>(R.id.containerSelectedPlats)
        container.removeAllViews()

        if (selectedPlats.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Aucun plat sélectionné"
            tv.setTextColor(0xFF888888.toInt())
            container.addView(tv)
            return
        }

        selectedPlats.sorted().forEach { plat ->
            val tv = TextView(this)
            tv.text = "• $plat"
            tv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            container.addView(tv)
        }
    }

    private fun saveFutureRecette() {
        val dateStorage = selectedDateStorage
        val description = findViewById<EditText>(R.id.etDescriptionFuture).text.toString().trim()

        if (dateStorage.isNullOrBlank()) {
            Toast.makeText(this, "Choisissez une date", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedPersonIds.isEmpty()) {
            Toast.makeText(this, "Sélectionnez au moins une personne", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedPlats.isEmpty()) {
            Toast.makeText(this, "Sélectionnez au moins un plat", Toast.LENGTH_SHORT).show()
            return
        }

        val dateSort = DateStorageUtils.toSortable(dateStorage)
        val todaySort = DateStorageUtils.toSortable(DateStorageUtils.todayStorageDate())
        if (dateSort != null && todaySort != null && dateSort < todaySort) {
            Toast.makeText(this, "La date doit être aujourd'hui ou future", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val db = dbHelper.getDatabase()
            val values = ContentValues().apply {
                put("nom_plat", selectedPlats.joinToString(","))
                put("id_personnes", selectedPersonIds.joinToString(","))
                put("date_repas", dateStorage)
                put("description", description)
            }

            if (futureId > 0) {
                db.update("future_repas", values, "id = ?", arrayOf(futureId.toString()))
                Toast.makeText(this, "Recette mise à jour", Toast.LENGTH_SHORT).show()
            } else {
                db.insert("future_repas", null, values)
                Toast.makeText(this, "Recette planifiée", Toast.LENGTH_SHORT).show()
            }

            finish()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

