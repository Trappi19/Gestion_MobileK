package com.example.gestion_mobilek

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ItemListActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var container: LinearLayout
    private lateinit var type: String  // "ingredient" ou "plat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_list)

        type = intent.getStringExtra("TYPE") ?: "ingredient"
        dbHelper = DatabaseHelper(this)
        container = findViewById(R.id.containerItems)

        val title = if (type == "ingredient") "Ingrédients" else "Plats"
        findViewById<TextView>(R.id.tvTitle).text = title

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnAdd).setOnClickListener {
            val intent = Intent(this, AddItemActivity::class.java)
            intent.putExtra("TYPE", type)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        container.removeAllViews()
        loadItems()
    }

    private fun loadItems() {
        try {
            val db = dbHelper.getDatabase()
            val query = if (type == "ingredient")
                "SELECT nom_ingredient FROM ingrédient ORDER BY nom_ingredient"
            else
                "SELECT nom_plat FROM plats ORDER BY nom_plat"

            val cursor = db.rawQuery(query, null)
            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(0)
                    val tv = TextView(this)
                    tv.text = "• $name"
                    tv.textSize = 16f
                    tv.setPadding(8, 16, 8, 16)
                    container.addView(tv)
                } while (cursor.moveToNext())
            } else {
                Toast.makeText(this, "Aucun élément", Toast.LENGTH_SHORT).show()
            }
            cursor.close()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
