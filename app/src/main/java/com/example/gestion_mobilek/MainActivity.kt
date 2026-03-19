package com.example.gestion_mobilek

import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.gestion_mobilek.databinding.ActivityMainBinding
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        updateStatsFromDB()   // TextView liés à la BDD


        binding.btnPersonnes.setOnClickListener {
            val intent = Intent(this, PersonListActivity::class.java)
            startActivity(intent)
        }

        binding.btnIngredients.setOnClickListener {
            val intent = Intent(this, ItemListActivity::class.java)
            intent.putExtra("TYPE", "ingredient")
            startActivity(intent)
        }

        binding.btnPlats.setOnClickListener {
            val intent = Intent(this, ItemListActivity::class.java)
            intent.putExtra("TYPE", "plat")
            startActivity(intent)
        }


        binding.btnFutureRecettes.setOnClickListener {
            Toast.makeText(this, "Ouvrir écran Futures Recettes", Toast.LENGTH_SHORT).show()
        }

        binding.btnHistorique.setOnClickListener {
            Toast.makeText(this, "Ouvrir Historique", Toast.LENGTH_SHORT).show()
        }

        binding.btnMenu.setOnClickListener {
            Toast.makeText(this, "Menu à implémenter", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatsFromDB() {
        try {
            val db = dbHelper.getDatabase()

            // Compteur plats
            val cursorPlats: Cursor =
                db.rawQuery("SELECT COUNT(*) FROM plats", null)
            cursorPlats.moveToFirst()
            val nbPlats = cursorPlats.getInt(0)
            cursorPlats.close()
            binding.tvStatsPlats.text = "Plats: $nbPlats"

            // Compteur historique
            val cursorHisto: Cursor =
                db.rawQuery("SELECT COUNT(*) FROM historique", null)
            cursorHisto.moveToFirst()
            val nbHisto = cursorHisto.getInt(0)
            cursorHisto.close()
            binding.tvStatsHistorique.text = "Historique: $nbHisto"

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
