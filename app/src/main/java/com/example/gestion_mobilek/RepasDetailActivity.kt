package com.example.gestion_mobilek

import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class RepasDetailActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_repas_detail)

        dbHelper = DatabaseHelper(this)

        val nomPlat = intent.getStringExtra("NOM_PLAT") ?: ""
        val idPersonnes = intent.getStringExtra("ID_PERSONNES") ?: ""
        val nbJours = intent.getIntExtra("NB_JOURS", 0)
        val description = intent.getStringExtra("DESCRIPTION") ?: ""

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Nom plat dans le bandeau
        findViewById<TextView>(R.id.tvNomPlat).text = nomPlat

        // Date traduite
        findViewById<TextView>(R.id.tvDate).text = "📅 ${nbJoursToDate(nbJours)}"

        // Description
        val tvDesc = findViewById<TextView>(R.id.tvDescription)
        if (description.isBlank()) {
            tvDesc.text = "Aucune description"
            tvDesc.setTextColor(0xFFAAAAAA.toInt())
        } else {
            tvDesc.text = description
            tvDesc.setTextColor(0xFF444444.toInt())
        }

        // Personnes
        loadPersonnes(idPersonnes)
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

    private fun nbJoursToDate(nbJours: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -nbJours)
        val jour = cal.get(Calendar.DAY_OF_MONTH)
        val mois = cal.get(Calendar.MONTH) + 1
        val annee = cal.get(Calendar.YEAR)
        return "$jour/$mois/$annee"
    }
}
