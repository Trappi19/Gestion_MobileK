package com.example.gestion_mobilek

import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PersonListActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_list)   // même nom que le XML

        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerPersons)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        loadPersonsFromDb()
    }

    private fun loadPersonsFromDb() {
        try {
            val db = dbHelper.getDatabase()
            val cursor: Cursor =
                db.rawQuery("SELECT id, nom FROM personnes ORDER BY nom", null)

            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(0)
                    val nom = cursor.getString(1)
                    addPersonButton(id, nom)
                } while (cursor.moveToNext())
            } else {
                Toast.makeText(this, "Aucune personne dans la base", Toast.LENGTH_SHORT).show()
            }

            cursor.close()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addPersonButton(personId: Int, name: String) {
        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8
            bottomMargin = 8
        }
        btn.text = name
        // Plus tard tu pourras ouvrir l'écran goûts ici
        btn.setOnClickListener {
            Toast.makeText(this, "Clique sur $name (id=$personId)", Toast.LENGTH_SHORT).show()
        }

        container.addView(btn)
    }
}
