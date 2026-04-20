package com.example.gestion_mobilek

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PersonMealsHistoryActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private var personId: Int = -1
    private var personName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_meals_history)

        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerPersonMeals)
        personId = intent.getIntExtra("PERSON_ID", -1)
        personName = intent.getStringExtra("PERSON_NAME").orEmpty()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTitle).text = if (personName.isBlank()) {
            "Repas participes"
        } else {
            "Repas de $personName"
        }
    }

    override fun onResume() {
        super.onResume()
        loadMeals()
    }

    private fun loadMeals() {
        container.removeAllViews()

        if (personId <= 0) {
            Toast.makeText(this, "Personne introuvable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val db = dbHelper.getDatabase()
            val dateConfig = RepasDateCompat.resolve(db)

            val cursor = if (dateConfig.isStorageDate) {
                val orderExpr = "SUBSTR(${dateConfig.columnName}, 5) || SUBSTR(${dateConfig.columnName}, 3, 2) || SUBSTR(${dateConfig.columnName}, 1, 2)"
                db.rawQuery(
                    """SELECT id, nom_plat, id_personnes, ${dateConfig.columnName}, description
                       FROM repas
                       WHERE (',' || REPLACE(IFNULL(id_personnes, ''), ' ', '') || ',') LIKE ?
                       ORDER BY $orderExpr DESC""",
                    arrayOf("%,$personId,%")
                )
            } else {
                db.rawQuery(
                    """SELECT id, nom_plat, id_personnes, ${dateConfig.columnName}, description
                       FROM repas
                       WHERE (',' || REPLACE(IFNULL(id_personnes, ''), ' ', '') || ',') LIKE ?
                       ORDER BY ${dateConfig.columnName} ASC""",
                    arrayOf("%,$personId,%")
                )
            }

            if (cursor.moveToFirst()) {
                do {
                    val repasId = cursor.getInt(0)
                    val platsCsv = cursor.getString(1) ?: ""
                    val personsCsv = cursor.getString(2) ?: ""
                    val storageDate = RepasDateCompat.cursorDateAsStorage(dateConfig, cursor.getString(3))
                    val description = cursor.getString(4) ?: ""
                    addMealRow(repasId, platsCsv, personsCsv, storageDate, description)
                } while (cursor.moveToNext())
            } else {
                val tv = TextView(this)
                tv.text = "Aucun repas trouve"
                tv.gravity = Gravity.CENTER
                tv.textSize = 16f
                tv.setPadding(0, 32, 0, 0)
                container.addView(tv)
            }
            cursor.close()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addMealRow(
        repasId: Int,
        platsCsv: String,
        personsCsv: String,
        storageDate: String?,
        description: String
    ) {
        val plats = platsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val platsText = when {
            plats.isEmpty() -> "Aucun plat"
            plats.size <= 2 -> plats.joinToString(", ")
            else -> "${plats[0]}, ${plats[1]}..."
        }

        val peopleNames = getPersonNames(personsCsv)
        val dateText = DateStorageUtils.displayFromStorage(storageDate)

        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8
            bottomMargin = 8
        }
        btn.textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_START
        btn.textSize = 14f
        btn.text = buildString {
            append("🍽️ ")
            append(platsText)
            append("\n👥 ")
            append(if (peopleNames.isBlank()) "Aucune personne" else peopleNames)
            append("\n📅 ")
            append(dateText)
            if (description.isNotBlank()) {
                append("\n📝 ")
                append(description)
            }
        }

        btn.setOnClickListener {
            startActivity(Intent(this, RepasDetailActivity::class.java).apply {
                putExtra("REPAS_ID", repasId)
                putExtra("NOM_PLAT", platsCsv)
                putExtra("ID_PERSONNES", personsCsv)
                putExtra("DATE_DERNIER_REPAS", storageDate)
                putExtra("DESCRIPTION", description)
            })
        }
        container.addView(btn)
    }

    private fun getPersonNames(idPersonnes: String): String {
        if (idPersonnes.isBlank()) return ""
        return try {
            val db = dbHelper.getDatabase()
            val ids = idPersonnes.split(",").filter { it.isNotBlank() }
            val names = mutableListOf<String>()
            ids.forEach { id ->
                val c = db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(id.trim()))
                if (c.moveToFirst()) names.add(c.getString(0))
                c.close()
            }
            names.joinToString(", ")
        } catch (_: SQLiteException) {
            ""
        }
    }
}

