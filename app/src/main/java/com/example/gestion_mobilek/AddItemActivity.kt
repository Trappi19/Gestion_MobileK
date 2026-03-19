package com.example.gestion_mobilek

import android.content.ContentValues
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AddItemActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var type: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        type = intent.getStringExtra("TYPE") ?: "ingredient"
        dbHelper = DatabaseHelper(this)

        val label = if (type == "ingredient") "Nom de l'ingrédient" else "Nom du plat"
        val hint = if (type == "ingredient") "Ex: Tomate" else "Ex: Lasagnes"
        val title = if (type == "ingredient") "Ajouter un ingrédient" else "Ajouter un plat"

        findViewById<TextView>(R.id.tvTitleItem).text = title
        findViewById<TextView>(R.id.tvLabelItem).text = label
        findViewById<EditText>(R.id.etItemName).hint = hint

        findViewById<ImageButton>(R.id.btnBackItem).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnConfirmItem).setOnClickListener {
            val raw = findViewById<EditText>(R.id.etItemName).text.toString().trim()
            if (raw.isEmpty()) {
                Toast.makeText(this, "Entrez un nom", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Majuscule forcée sur la première lettre
            val name = raw.replaceFirstChar { it.uppercase() }

            try {
                val db = dbHelper.getDatabase()
                val values = ContentValues()

                if (type == "ingredient") {
                    values.put("nom_ingredient", name)
                    db.insert("ingrédient", null, values)
                } else {
                    values.put("nom_plat", name)
                    db.insert("plats", null, values)
                }

                Toast.makeText(this, "$name ajouté !", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)  // Signale à AddPersonActivity de rouvrir le dialog
                finish()
            } catch (e: SQLiteException) {
                Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
