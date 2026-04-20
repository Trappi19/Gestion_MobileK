package com.example.gestion_mobilek

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddEditFutureRecetteActivity : AppCompatActivity() {

    private data class PersonOption(val id: Int, val name: String)
    private data class ReminderDraft(val triggerAtMillis: Long)

    private lateinit var dbHelper: DatabaseHelper
    private var futureId: Int = -1
    private var selectedDateStorage: String? = null
    private var futureDateColumn: String = "date_dernier_repas"
    private val selectedPersonIds = mutableSetOf<Int>()
    private val selectedPlats = mutableSetOf<String>()
    private val reminderDrafts = mutableListOf<ReminderDraft>()
    private var remindersEnabled = false

    companion object {
        private const val REQUEST_ADD_PERSON = 3001
        private const val REQUEST_ADD_PLAT = 3002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_future_recette)

        dbHelper = DatabaseHelper(this)
        futureId = intent.getIntExtra("FUTURE_ID", -1)

        try {
            val db = dbHelper.getDatabase()
            FutureRecettesManager.ensureSchema(db)
            futureDateColumn = resolveFutureDateColumn(db)
            FutureReminderStore.ensureSchema(db)
        } catch (_: SQLiteException) {
        }

        val tvTitle = findViewById<TextView>(R.id.tvTitleFuture)
        val tvDate = findViewById<TextView>(R.id.tvSelectedDate)
        val cbReminders = findViewById<CheckBox>(R.id.cbEnableReminders)

        findViewById<ImageButton>(R.id.btnBackAdd).setOnClickListener { finish() }

        if (futureId > 0) {
            tvTitle.text = "Modifier recette planifiée"
            findViewById<Button>(R.id.btnConfirmFuture).text = "Sauvegarder"
            loadExistingData(tvDate)
            loadExistingReminders()
        } else {
            val preselectedIds = intent.getIntArrayExtra("PRESELECTED_PERSON_IDS")
                ?.filter { it > 0 }
                .orEmpty()
            if (preselectedIds.isNotEmpty()) {
                selectedPersonIds.clear()
                selectedPersonIds.addAll(preselectedIds)
                refreshPersonsView()
            }
        }

        cbReminders.setOnCheckedChangeListener { _, checked ->
            remindersEnabled = checked
            updateReminderSectionVisibility()
        }

        findViewById<Button>(R.id.btnAddReminder).setOnClickListener {
            showReminderPicker()
        }

        updateReminderSectionVisibility()

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
                "SELECT nom_plat, id_personnes, $futureDateColumn, description FROM future_repas WHERE id = ?",
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

    private fun loadExistingReminders() {
        try {
            val db = dbHelper.getDatabase()
            reminderDrafts.clear()
            reminderDrafts.addAll(
                FutureReminderStore.loadForFuture(db, futureId)
                    .map { ReminderDraft(it.triggerAtMillis) }
            )
            remindersEnabled = reminderDrafts.isNotEmpty()
            findViewById<CheckBox>(R.id.cbEnableReminders).isChecked = remindersEnabled
            updateReminderSectionVisibility()
            refreshRemindersView()
        } catch (_: SQLiteException) {
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

    private fun showReminderPicker() {
        val now = Calendar.getInstance()
        val initial = Calendar.getInstance()

        selectedDateStorage?.let { raw ->
            val normalized = DateStorageUtils.normalizeStorageDate(raw)
            if (!normalized.isNullOrBlank()) {
                val day = normalized.substring(0, 2).toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)
                val month = (normalized.substring(2, 4).toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)) - 1
                val year = normalized.substring(4, 8).toIntOrNull() ?: now.get(Calendar.YEAR)
                initial.set(year, month, day)
            }
        }

        DatePickerDialog(
            this,
            { _, y, m, d ->
                val timeSeed = Calendar.getInstance()
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val picked = Calendar.getInstance().apply {
                            set(Calendar.YEAR, y)
                            set(Calendar.MONTH, m)
                            set(Calendar.DAY_OF_MONTH, d)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val pickedMillis = picked.timeInMillis
                        if (pickedMillis <= System.currentTimeMillis()) {
                            Toast.makeText(this, "Choisissez une date/heure future", Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }
                        remindersEnabled = true
                        findViewById<CheckBox>(R.id.cbEnableReminders).isChecked = true
                        addReminderDraft(pickedMillis)
                    },
                    timeSeed.get(Calendar.HOUR_OF_DAY),
                    timeSeed.get(Calendar.MINUTE),
                    true
                ).show()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun addReminderDraft(triggerAtMillis: Long) {
        if (reminderDrafts.any { it.triggerAtMillis == triggerAtMillis }) {
            Toast.makeText(this, "Ce rappel existe déjà", Toast.LENGTH_SHORT).show()
            return
        }
        reminderDrafts.add(ReminderDraft(triggerAtMillis))
        reminderDrafts.sortBy { it.triggerAtMillis }
        remindersEnabled = true
        updateReminderSectionVisibility()
        refreshRemindersView()
    }

    private fun updateReminderSectionVisibility() {
        findViewById<View>(R.id.layoutReminderSection).visibility = if (remindersEnabled) View.VISIBLE else View.GONE
        findViewById<CheckBox>(R.id.cbEnableReminders).isChecked = remindersEnabled
    }

    private fun refreshRemindersView() {
        val container = findViewById<LinearLayout>(R.id.containerReminders)
        container.removeAllViews()

        if (reminderDrafts.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.future_reminders_empty)
            tv.setTextColor(0xFF888888.toInt())
            container.addView(tv)
            return
        }

        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        reminderDrafts.forEachIndexed { index, reminder ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 6 }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val tv = TextView(this).apply {
                text = formatter.format(Date(reminder.triggerAtMillis))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnRemove = ImageButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48)
                setBackgroundResource(android.R.color.transparent)
                setImageResource(android.R.drawable.ic_menu_delete)
                contentDescription = getString(R.string.future_reminders_remove)
                setOnClickListener {
                    reminderDrafts.removeAt(index)
                    refreshRemindersView()
                }
            }

            row.addView(tv)
            row.addView(btnRemove)
            container.addView(row)
        }
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
                    .setTitle("Personnes présentes")
                    .setMessage("Aucune personne disponible.")
                    .setPositiveButton("+ Nouvelle personne") { _, _ -> launchQuickAddPerson() }
                    .setNegativeButton("Annuler", null)
                    .show()
                return
            }

            val initial = options.filter { selectedPersonIds.contains(it.id) }.toSet()
            SearchableMultiSelectDialog.show(
                context = this,
                title = "Personnes présentes",
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
                    .setTitle("Plats possibles")
                    .setMessage("Aucun plat disponible.")
                    .setPositiveButton("+ Nouveau plat") { _, _ -> launchQuickAddPlat() }
                    .setNegativeButton("Annuler", null)
                    .show()
                return
            }

            SearchableMultiSelectDialog.show(
                context = this,
                title = "Plats possibles",
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
        if (remindersEnabled && reminderDrafts.isEmpty()) {
            Toast.makeText(this, "Ajoutez au moins un rappel ou désactivez-les", Toast.LENGTH_SHORT).show()
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
            val isUpdate = futureId > 0
            if (isUpdate) {
                FutureReminderScheduler.cancelFutureReminders(this, futureId, deleteRows = false)
            }
            db.beginTransaction()
            val savedFutureId: Int
            try {
                val values = ContentValues().apply {
                    put("nom_plat", selectedPlats.joinToString(","))
                    put("id_personnes", selectedPersonIds.joinToString(","))
                    put(futureDateColumn, dateStorage)
                    put("description", description)
                }

                savedFutureId = if (futureId > 0) {
                    db.update("future_repas", values, "id = ?", arrayOf(futureId.toString()))
                    futureId
                } else {
                    val newId = db.insert("future_repas", null, values)
                    if (newId <= 0) throw SQLiteException("Impossible de créer la future recette")
                    futureId = newId.toInt()
                    futureId
                }

                if (remindersEnabled) {
                    FutureReminderStore.replaceForFuture(db, savedFutureId, reminderDrafts.map { it.triggerAtMillis })
                } else {
                    FutureReminderStore.deleteForFuture(db, savedFutureId)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            FutureReminderScheduler.scheduleForFuture(this, savedFutureId)
            Toast.makeText(this, if (isUpdate) "Recette mise à jour" else "Recette planifiée", Toast.LENGTH_SHORT).show()
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

    private fun resolveFutureDateColumn(db: android.database.sqlite.SQLiteDatabase): String {
        val cols = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(future_repas)", null).use { c ->
            if (c.moveToFirst()) {
                do {
                    cols.add(c.getString(1))
                } while (c.moveToNext())
            }
        }
        return if (cols.contains("date_dernier_repas")) "date_dernier_repas" else "date_repas"
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

