package com.example.gestion_mobilek.ui.future

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

import android.Manifest
import android.accounts.AccountManager
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes

class FutureRecetteDetailActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var futureId: Int = -1
    private var sourceMode: Int = 0
    private var sourceTable: String = "future_repas"
    private var sourceDateColumn: String = FutureRecettesManager.NEW_DATE_COL
    private var currentDescription: String = ""
    private var currentNomPlat: String = ""
    private var currentIdPersonnes: String = ""
    private var currentDateStorage: String = ""

    companion object {
        private const val REQUEST_ACCOUNT_PICKER = 5001
        private const val REQUEST_GOOGLE_SIGN_IN = 5002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_future_recette_detail)

        dbHelper = DatabaseHelper(this)
        futureId = intent.getIntExtra("FUTURE_ID", -1)
        sourceMode = intent.getIntExtra("SOURCE_MODE", if (SettingsStore.isExternalDataSourceEnabled(this)) 1 else 0)
        try {
            val db = dbHelper.getDatabaseForMode(sourceMode != 0)
            val sourceConfig = FutureRecettesManager.resolveSourceConfig(this, db)
            sourceTable = sourceConfig.tableName
            sourceDateColumn = sourceConfig.dateColumn
            // Migration colonne google_event_id
            GoogleCalendarHelper.ensureGoogleEventIdColumn(db)
        } catch (_: SQLiteException) {
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEdit).setOnClickListener {
            if (futureId <= 0) return@setOnClickListener
            startActivity(Intent(this, AddEditFutureRecetteActivity::class.java).apply {
                putExtra("FUTURE_ID", futureId)
                putExtra("SOURCE_MODE", sourceMode)
            })
        }
        findViewById<ImageButton>(R.id.btnDelete).setOnClickListener {
            if (futureId <= 0) return@setOnClickListener
            confirmDelete()
        }
        findViewById<TextView>(R.id.btnEditDescription).setOnClickListener {
            showEditDescriptionDialog()
        }

        // Bouton Google Calendar
        val btnGcal = findViewById<Button>(R.id.btnAddToGoogleCalendar)
        btnGcal?.setOnClickListener {
            launchGoogleCalendarFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        loadDetail()
    }

    private fun loadDetail() {
        if (futureId <= 0) {
            Toast.makeText(this, "Recette introuvable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val db = dbHelper.getDatabaseForMode(sourceMode != 0)
            val sourceConfig = FutureRecettesManager.resolveSourceConfig(this, db)
            sourceTable = sourceConfig.tableName
            sourceDateColumn = sourceConfig.dateColumn
            GoogleCalendarHelper.ensureGoogleEventIdColumn(db)

            val c = db.rawQuery(
                "SELECT nom_plat, id_personnes, $sourceDateColumn, description, ${GoogleCalendarHelper.COLUMN_GOOGLE_EVENT_ID} FROM $sourceTable WHERE id = ?",
                arrayOf(futureId.toString())
            )

            if (!c.moveToFirst()) {
                c.close()
                Toast.makeText(this, "Recette supprimée", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            currentNomPlat = c.getString(0) ?: ""
            currentIdPersonnes = c.getString(1) ?: ""
            val dateRepas = c.getString(2)
            currentDescription = c.getString(3) ?: ""
            val existingEventId = c.getString(4)?.takeIf { it.isNotBlank() }
            currentDateStorage = DateStorageUtils.normalizeStorageDate(dateRepas) ?: ""
            c.close()

            findViewById<TextView>(R.id.tvNomPlat).text = if (currentNomPlat.isBlank()) "Recette planifiée" else currentNomPlat
            findViewById<TextView>(R.id.tvDate).text = "📅 ${DateStorageUtils.displayFromStorage(dateRepas)}"
            loadPersonnes(currentIdPersonnes)

            val tvDesc = findViewById<TextView>(R.id.tvDescription)
            if (currentDescription.isBlank()) {
                tvDesc.text = "Aucune description"
                tvDesc.setTextColor(0xFF888888.toInt())
            } else {
                tvDesc.text = currentDescription
                tvDesc.setTextColor(0xFF444444.toInt())
            }

            // Label bouton Google Calendar
            val btnGcal = findViewById<Button>(R.id.btnAddToGoogleCalendar)
            btnGcal?.text = if (existingEventId != null) "Mettre à jour l'événement" else "Ajouter à Google Calendar"

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Google Calendar flow ─────────────────────────────────────────────

    private fun launchGoogleCalendarFlow() {
        // Vérifier si un compte Google est déjà connecté avec les bons scopes
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && GoogleSignIn.hasPermissions(
                account,
                Scope(CalendarScopes.CALENDAR_EVENTS)
            )
        ) {
            pushToGoogleCalendar(account.email ?: "")
        } else {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(CalendarScopes.CALENDAR_EVENTS))
                .build()
            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
            startActivityForResult(client.signInIntent, REQUEST_GOOGLE_SIGN_IN)
        }
    }

    private fun pushToGoogleCalendar(accountEmail: String) {
        if (accountEmail.isBlank()) {
            Toast.makeText(this, "Aucun compte Google connecté", Toast.LENGTH_SHORT).show()
            return
        }

        val btnGcal = findViewById<Button>(R.id.btnAddToGoogleCalendar)
        btnGcal?.isEnabled = false
        btnGcal?.text = "En cours…"

        // Résoudre noms personnes
        val personnesNoms: String = try {
            val db = dbHelper.getDatabaseForMode(sourceMode != 0)
            val ids = currentIdPersonnes.split(",").filter { it.isNotBlank() }
            val names = ids.mapNotNull { id ->
                val c = db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(id.trim()))
                val name = if (c.moveToFirst()) c.getString(0) else null
                c.close()
                name
            }
            names.joinToString(", ")
        } catch (_: SQLiteException) { "" }

        Thread {
            try {
                val db = dbHelper.getDatabaseForMode(sourceMode != 0)
                val existingEventId = GoogleCalendarHelper.readGoogleEventId(db, futureId, sourceTable)
                val service = GoogleCalendarHelper.buildCalendarService(this, accountEmail)
                val nomDisplay = currentNomPlat.replace(",", ", ").ifBlank { "Repas planifié" }

                val eventId = if (existingEventId != null) {
                    GoogleCalendarHelper.updateEvent(
                        service, existingEventId, nomDisplay, personnesNoms, currentDescription, currentDateStorage
                    )
                    existingEventId
                } else {
                    GoogleCalendarHelper.createEvent(
                        service, nomDisplay, personnesNoms, currentDescription, currentDateStorage
                    )
                }

                GoogleCalendarHelper.saveGoogleEventId(db, futureId, eventId, sourceTable)

                runOnUiThread {
                    btnGcal?.isEnabled = true
                    btnGcal?.text = "Mettre à jour l'événement"
                    Toast.makeText(
                        this,
                        if (existingEventId != null) "Événement mis à jour !" else "Ajouté à Google Calendar !",
                        Toast.LENGTH_SHORT
                    ).show()
       