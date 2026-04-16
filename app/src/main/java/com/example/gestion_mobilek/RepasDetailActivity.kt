package com.example.gestion_mobilek

import android.app.AlertDialog
import android.content.ContentValues
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RepasDetailActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var repasId: Int = -1
    private var currentDescription: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_repas_detail)

        dbHelper = DatabaseHelper(this)

        repasId = intent.getIntExtra("REPAS_ID", -1)
        val nomPlat = intent.getStringExtra("NOM_PLAT") ?: ""
        val idPersonnes = intent.getStringExtra("ID_PERSONNES") ?: ""
        val legacyNbJours = if (intent.hasExtra("NB_JOURS")) intent.getIntExtra("NB_JOURS", -1) else -1
        val dateDernierRepas = intent.getStringExtra("DATE_DERNIER_REPAS")
            ?: if (legacyNbJours >= 0) DateStorageUtils.normalizeStorageDate(legacyNbJours.toString()) else null
        currentDescription = intent.getStringExtra("DESCRIPTION") ?: ""

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Nom plat dans le bandeau
        findViewById<TextView>(R.id.tvNomPlat).text = nomPlat

        // Date traduite
        findViewById<TextView>(R.id.tvDate).text = "📅 ${DateStorageUtils.displayFromStorage(dateDernierRepas)}"

        // Description
        renderDescription(currentDescription)
        findViewById<TextView>(R.id.btnEditDescription).setOnClickListener {
            showEditDescriptionDialog()
        }

        // Personnes
        loadPersonnes(idPersonnes)
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
