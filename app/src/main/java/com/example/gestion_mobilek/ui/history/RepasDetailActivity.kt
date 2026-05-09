package com.example.gestion_mobilek.ui.history

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

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes

class RepasDetailActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var repasId: Int = -1
    private var currentDescription: String = ""
    private var currentNomPlat: String = ""
    private var currentIdPersonnes: String = ""
    private var currentDateStorage: String = ""

    companion object {
        private const val REQUEST_GOOGLE_SIGN_IN = 5003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_repas_detail)

        dbHelper = DatabaseHelper(this)

        repasId = intent.getIntExtra("REPAS_ID", -1)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnEditDescription).setOnClickListener {
            showEditDescriptionDialog()
        }
        findViewById<Button>(R.id.btnAddToGoogleCalendar).setOnClickListener {
            launchGoogleCalendarFlow()
        }

        // Si les données sont passées en intent (ancien chemin), on les utilise directement
        // Sinon on charge depuis la BDD (chemin CalendarActivity)
        val nomPlatExtra = intent.getStringExtra("NOM_PLAT")
        if (nomPlatExtra != null) {
            currentNomPlat = nomPlatExtra
            currentIdPersonnes = intent.getStringExtra("ID_PERSONNES") ?: ""
            val legacyNbJours = if (intent.hasExtra("NB_JOURS")) intent.getIntExtra("NB_JOURS", -1) else -1
            val dateDernierRepas = intent.getStringExtra("DATE_DERNIER_REPAS")
                ?: if (legacyNbJours >= 0) DateStorageUtils.normalizeStorageDate(legacyNbJours.toString()) else null
            currentDateStorage = DateStorageUtils.normalizeStorageDate(dateDernierRepas) ?: ""
            currentDescription = intent.getStringExtra("DESCRIPTION") ?: ""
            findViewById<TextView>(R.id.tvNomPlat).text = currentNomPlat
            findViewById<TextView>(R.id.tvDate).text = "📅 ${DateStorageUtils.displayFromStorage(dateDernierRepas)}"
            renderDescription(currentDescription)
            loadPersonnes(currentIdPersonnes)
            updateGcalButton()
        } else {
            loadFromDatabase()
        }
    }

    private fun loadFromDatabase() {
        if (repasId <= 0) {
            Toast.makeText(this, "Repas introuvable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        try {
            val db = dbHelper.getDatabase()
            GoogleCalendarHelper.ensureGoogleEventIdColumn(db, "repas")
            val c = db.rawQuery(
                "SELECT nom_plat, id_personnes, date_dernier_repas, description FROM repas WHERE id = ?",
                arrayOf(repasId.toString())
            )
            if (!c.moveToFirst()) {
                c.close()
                Toast.makeText(this, "Repas introuvable", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            currentNomPlat = c.getString(0) ?: ""
            currentIdPersonnes = c.getString(1) ?: ""
            val dateRaw = c.getString(2) ?: ""
            currentDescription = c.getString(3) ?: ""
            currentDateStorage = DateStorageUtils.normalizeStorageDate(dateRaw) ?: ""
            c.close()

            findViewById<TextView>(R.id.tvNomPlat).text = currentNomPlat
            findViewById<TextView>(R.id.tvDate).text = "📅 ${DateStorageUtils.displayFromStorage(currentDateStorage)}"
            renderDescription(currentDescription)
            loadPersonnes(currentIdPersonnes)
            updateGcalButton()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateGcalButton() {
        val btn = findViewById<Button>(R.id.btnAddToGoogleCalendar)
        try {
            val db = dbHelper.getDatabase()
            GoogleCalendarHelper.ensureGoogleEventIdColumn(db, "repas")
            val existingId = GoogleCalendarHelper.readGoogleEventId(db, repasId, "repas")
            btn.text = if (existingId != null) "Mettre à jour l'événement" else "Ajouter à Google Calendar"
        } catch (_: SQLiteException) {}
    }

    private fun launchGoogleCalendarFlow() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR_EVENTS))) {
            pushToGoogleCalendar(account.email ?: "")
        } else {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(CalendarScopes.CALENDAR_EVENTS))
                .build()
            startActivityForResult(GoogleSignIn.getClient(this, gso).signInIntent, REQUEST_GOOGLE_SIGN_IN)
        }
    }

    private fun pushToGoogleCalendar(accountEmail: String) {
        val btn = findViewById<Button>(R.id.btnAddToGoogleCalendar)
        btn.isEnabled = false
        btn.text = "En cours…"

        val personnesNoms: String = try {
            val db = dbHelper.getDatabase()
            currentIdPersonnes.split(",").filter { it.isNotBlank() }.mapNotNull { id ->
                val c = db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(id.trim()))
                val name = if (c.moveToFirst()) c.getString(0) else null
                c.close()
                name
            }.joinToString(", ")
        } catch (_: SQLiteException) { "" }

        Thread {
            try {
                val db = dbHelper.getDatabase()
                val existingEventId = GoogleCalendarHelper.readGoogleEventId(db, repasId, "repas")
                val service = GoogleCalendarHelper.buildCalendarService(this, accountEmail)
                val nomDisplay = currentNomPlat.replace(",", ", ").ifBlank { "Repas" }

                val eventId = if (existingEventId != null) {
                    GoogleCalendarHelper.updateEvent(service, existingEventId, nomDisplay, personnesNoms, currentDescription, currentDateStorage)
                    existingEventId
                } else {
                    GoogleCalendarHelper.createEvent(service, nomDisplay, personnesNoms, currentDescription, currentDateStorage)
                }

                GoogleCalendarHelper.saveGoogleEventId(db, repasId, eventId, "repas")

                runOnUiThread {
                    btn.isEnabled = true
                    btn.text = "Mettre à jour l'événement"
                    Toast.makeText(this, if (existingEventId != null) "Événement mis à jour !" else "Ajouté à Google Calendar !", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btn.isEnabled = true
                    updateGcalButton()
                    Toast.makeText(this, "Erreur : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                pushToGoogleCalendar(task.result?.email ?: "")
            } else {
                val e = task.exception
                val statusCode = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                val msg = "Sign-in échoué — code=$statusCode msg=${e?.message}"
                android.util.Log.e("GoogleSignIn", msg, e)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderDescription(value: String) {
        val tvDesc = findViewById<TextView>(R.id.tvDescription)
        if (value.isBlank()) {
            tvDesc.text = "Aucune description"
            tvDesc.setTextColor(0xFFAAAAAA.toInt())
        } else {
            tvDesc.text = value
            tvDesc.setTextColor(0xFF444444.toInt())
        }
    }

    private fun showEditDescriptionDialog() {
        if (repasId <= 0) {
            Toast.makeText(this, "Modification indisponible pour ce repas", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            setText(currentDescription)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            hint = "Ajouter une note utile sur ce repas"
            setSelection(text.length)
        }

        val wrapper = LinearLayout(this).apply {
            setPadding(48, 20, 48, 0)
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        AlertDialog.Builder(this)
            .setTitle("Modifier la description")
            .setView(wrapper)
            .setPositiveButton("Enregistrer") { _, _ ->
                saveDescription(input.text.toString().trim())
            }
            .setNeutralButton("Vider") { _, _ ->
                saveDescription("")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saveDescription(newValue: String) {
        try {
            val values = ContentValues().apply { put("description", newValue) }
            dbHelper.getDatabase().update("repas", values, "id = ?", arrayOf(repasId.toString()))
            currentDescription = newValue
            renderDescription(currentDescription)
            Toast.makeText(this, "Description mise a jour", Toast.LENGTH_SHORT).show()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPersonnes(idPersonnes: String) {
        val container = findViewById<LinearLayout>(R.id.containerPersonnes)
        container.removeAllViews()

        if (idPersonnes.isBlank()) {
            addPersonneRow(container, "Aucune personne", grise = true)
            return
        }

        try {
            val db = dbHelper.getDatabase()
            val ids = idPersonnes.split(",").filter { it.isNotBlank() }

            if (ids.isEmpty()) {
                addPersonneRow(container, "Aucune personne", grise = true)
                return
            }

            ids.forEach { id ->
                val cursor = db.rawQuery(
                    "SELECT nom FROM personnes WHERE id = ?",
                    arrayOf(id.trim())
                )
                if (cursor.moveToFirst()) {
                    addPersonneRow(container, "👤 ${cursor.getString(0)}", grise = false)
                } else {
                    // ID existe dans repas mais plus dans personnes (supprimée)
                    addPersonneRow(container, "👤 Personne inconnue (#$id)", grise = true)
                }
                cursor.close()
            }
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addPersonneRow(container: LinearLayout, text: String, grise: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 15f
        tv.setPadding(0, 10, 0, 10)
        tv.setTextColor(if (grise) 0xFFAAAAAA.toInt() else 0xFF222222.toInt())
        tv.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 4 }
        container.addView(tv)
    }

}
