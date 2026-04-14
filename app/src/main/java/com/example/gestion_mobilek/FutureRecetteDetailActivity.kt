package com.example.gestion_mobilek

import android.app.AlertDialog
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FutureRecetteDetailActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var futureId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_future_recette_detail)

        dbHelper = DatabaseHelper(this)
        futureId = intent.getIntExtra("FUTURE_ID", -1)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEdit).setOnClickListener {
            if (futureId <= 0) return@setOnClickListener
            startActivity(Intent(this, AddEditFutureRecetteActivity::class.java).apply {
                putExtra("FUTURE_ID", futureId)
            })
        }
        findViewById<ImageButton>(R.id.btnDelete).setOnClickListener {
            if (futureId <= 0) return@setOnClickListener
            confirmDelete()
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
            val db = dbHelper.getDatabase()
            val c = db.rawQuery(
                "SELECT nom_plat, id_personnes, date_repas, description FROM future_repas WHERE id = ?",
                arrayOf(futureId.toString())
            )

            if (!c.moveToFirst()) {
                c.close()
                Toast.makeText(this, "Recette supprimée", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val nomPlat = c.getString(0) ?: ""
            val idPersonnes = c.getString(1) ?: ""
            val dateRepas = c.getString(2)
            val description = c.getString(3) ?: ""
            c.close()

            findViewById<TextView>(R.id.tvNomPlat).text = if (nomPlat.isBlank()) "Recette planifiée" else nomPlat
            findViewById<TextView>(R.id.tvDate).text = "📅 ${DateStorageUtils.displayFromStorage(dateRepas)}"
            loadPersonnes(idPersonnes)

            val tvDesc = findViewById<TextView>(R.id.tvDescription)
            if (description.isBlank()) {
                tvDesc.text = "Aucune description"
                tvDesc.setTextColor(0xFF888888.toInt())
            } else {
                tvDesc.text = description
                tvDesc.setTextColor(0xFF444444.toInt())
            }
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPersonnes(idPersonnes: String) {
        val container = findViewById<LinearLayout>(R.id.containerPersonnes)
        container.removeAllViews()

        if (idPersonnes.isBlank()) {
            addPersonRow(container, "Aucune personne", true)
            return
        }

        try {
            val db = dbHelper.getDatabase()
            val ids = idPersonnes.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (ids.isEmpty()) {
                addPersonRow(container, "Aucune personne", true)
                return
            }

            ids.forEach { id ->
                val c = db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(id))
                if (c.moveToFirst()) {
                    addPersonRow(container, "👤 ${c.getString(0)}", false)
                } else {
                    addPersonRow(container, "👤 Personne inconnue (#$id)", true)
                }
                c.close()
            }
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addPersonRow(container: LinearLayout, text: String, gray: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 15f
        tv.setPadding(0, 10, 0, 10)
        tv.setTextColor(if (gray) 0xFFAAAAAA.toInt() else 0xFF222222.toInt())
        tv.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 4 }
        container.addView(tv)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer cette recette ?")
            .setMessage("Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    dbHelper.getDatabase().delete("future_repas", "id = ?", arrayOf(futureId.toString()))
                    Toast.makeText(this, "Recette supprimée", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: SQLiteException) {
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}


