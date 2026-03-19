package com.example.gestion_mobilek

import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class PersonListActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_list)

        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerPersons)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Bouton "+" → ouvre AddPersonActivity
        findViewById<ImageButton>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddPersonActivity::class.java))
        }
    }

    // onResume pour recharger la liste quand on revient de AddPersonActivity
    override fun onResume() {
        super.onResume()
        container.removeAllViews()
        loadPersonsFromDb()
    }

    private fun loadPersonsFromDb() {
        try {
            val db = dbHelper.getDatabase()
            val cursor: Cursor = db.rawQuery(
                "SELECT id, nom, dernier_passage FROM personnes ORDER BY nom", null
            )

            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(0)
                    val nom = cursor.getString(1)
                    val daysAgo = cursor.getInt(2)
                    addPersonButton(id, nom, daysAgo)
                } while (cursor.moveToNext())
            } else {
                Toast.makeText(this, "Aucune personne dans la base", Toast.LENGTH_SHORT).show()
            }
            cursor.close()

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun daysAgoToDateString(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        return "$day/$month/$year"
    }

    private fun addPersonButton(personId: Int, name: String, daysAgo: Int) {
        val dateStr = daysAgoToDateString(daysAgo)

        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8
            bottomMargin = 8
        }

        // Nom à gauche + date à droite dans le texte
        btn.text = "$name     |     $dateStr"
        btn.setOnClickListener {
            Toast.makeText(this, "Ouvrir détail de $name", Toast.LENGTH_SHORT).show()
            // TODO: Intent vers PersonDetailActivity
        }
        container.addView(btn)
    }
}
