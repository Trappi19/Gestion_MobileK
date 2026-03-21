package com.example.gestion_mobilek

import android.content.ContentValues
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PersonDetailActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var personId: Int = -1
    private lateinit var personName: String

    // Listes en mémoire (depuis BDD)
    private val likedIngredients = mutableListOf<String>()
    private val likedPlats = mutableListOf<String>()
    private val dislikedIngredients = mutableListOf<String>()
    private val dislikedPlats = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_detail)

        personId = intent.getIntExtra("PERSON_ID", -1)
        personName = intent.getStringExtra("PERSON_NAME") ?: "?"
        dbHelper = DatabaseHelper(this)

        findViewById<TextView>(R.id.tvPersonName).text = personName
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Boutons "+" aime / n'aime pas
        findViewById<ImageButton>(R.id.btnAddLike).setOnClickListener {
            showPickerDialog(isLike = true)
        }
        findViewById<ImageButton>(R.id.btnAddDislike).setOnClickListener {
            showPickerDialog(isLike = false)
        }

        loadGoûtsFromDb()
    }

    private fun loadGoûtsFromDb() {
        try {
            val db = dbHelper.getDatabase()
            val cursor = db.rawQuery(
                "SELECT aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat FROM gouts WHERE id_personne = ?",
                arrayOf(personId.toString())
            )

            likedIngredients.clear()
            likedPlats.clear()
            dislikedIngredients.clear()
            dislikedPlats.clear()

            if (cursor.moveToFirst()) {
                cursor.getString(0)?.split(",")?.filter { it.isNotBlank() }?.let { likedIngredients.addAll(it) }
                cursor.getString(1)?.split(",")?.filter { it.isNotBlank() }?.let { dislikedIngredients.addAll(it) }
                cursor.getString(2)?.split(",")?.filter { it.isNotBlank() }?.let { likedPlats.addAll(it) }
                cursor.getString(3)?.split(",")?.filter { it.isNotBlank() }?.let { dislikedPlats.addAll(it) }
            }
            // Pas de else → si pas de ligne gouts, listes restent vides, rien affiché
            cursor.close()

            refreshLikesView()
            refreshDislikesView()
        } catch (e: SQLiteException) {
            // Silencieux aussi en cas d'erreur BDD
        }
    }


    private fun refreshLikesView() {
        val container = findViewById<LinearLayout>(R.id.containerLikes)
        container.removeAllViews()
        val allLiked = likedIngredients.map { "🧄 $it" } + likedPlats.map { "🍽️ $it" }
        allLiked.forEach { addTextItem(container, it) }
        // Plus de else → liste vide = rien affiché
    }

    private fun refreshDislikesView() {
        val container = findViewById<LinearLayout>(R.id.containerDislikes)
        container.removeAllViews()
        val allDisliked = dislikedIngredients.map { "🧄 $it" } + dislikedPlats.map { "🍽️ $it" }
        allDisliked.forEach { addTextItem(container, it) }
    }


    private fun addTextItem(container: LinearLayout, text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 14f
        tv.setPadding(4, 10, 4, 10)
        tv.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 4 }
        container.addView(tv)
    }

    // Dialog multi-sélection ingrédients + plats séparés
    private fun showPickerDialog(isLike: Boolean) {
        val db = dbHelper.getDatabase()

        // Récupère ingrédients et plats depuis BDD
        val ingredients = mutableListOf<String>()
        db.rawQuery("SELECT nom_ingredient FROM ingrédient ORDER BY nom_ingredient", null).use { c ->
            if (c.moveToFirst()) do { ingredients.add(c.getString(0)) } while (c.moveToNext())
        }
        val plats = mutableListOf<String>()
        db.rawQuery("SELECT nom_plat FROM plats ORDER BY nom_plat", null).use { c ->
            if (c.moveToFirst()) do { plats.add(c.getString(0)) } while (c.moveToNext())
        }

        // Fusionne avec séparateur lisible
        val allItems = (ingredients.map { "🧄 $it" } + listOf("── Plats ──") + plats.map { "🍽️ $it" })
        val currentLikedIng = if (isLike) likedIngredients else dislikedIngredients
        val currentLikedPlat = if (isLike) likedPlats else dislikedPlats

        val checked = BooleanArray(allItems.size) { idx ->
            when {
                idx < ingredients.size -> currentLikedIng.contains(ingredients[idx])
                idx == ingredients.size -> false  // séparateur
                else -> currentLikedPlat.contains(plats[idx - ingredients.size - 1])
            }
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(if (isLike) "Aime" else "N'aime pas")
            .setMultiChoiceItems(allItems.toTypedArray(), checked) { _, which, isChecked ->
                if (which == ingredients.size) return@setMultiChoiceItems  // séparateur non cliquable
                if (which < ingredients.size) {
                    val name = ingredients[which]
                    if (isChecked) { if (!currentLikedIng.contains(name)) currentLikedIng.add(name) }
                    else currentLikedIng.remove(name)
                } else {
                    val name = plats[which - ingredients.size - 1]
                    if (isChecked) { if (!currentLikedPlat.contains(name)) currentLikedPlat.add(name) }
                    else currentLikedPlat.remove(name)
                }
            }
            .setPositiveButton("OK") { _, _ -> saveGoûtsToDb(isLike) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saveGoûtsToDb(isLike: Boolean) {
        try {
            val db = dbHelper.getDatabase()

            // Recharge d'abord ce qui existe déjà pour ne pas écraser l'autre colonne
            val cursor = db.rawQuery(
                "SELECT aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat FROM gouts WHERE id_personne = ?",
                arrayOf(personId.toString())
            )

            val existingLikedIng: MutableList<String>
            val existingLikedPlat: MutableList<String>
            val existingDislikedIng: MutableList<String>
            val existingDislikedPlat: MutableList<String>

            if (cursor.moveToFirst()) {
                existingLikedIng = cursor.getString(0)?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
                existingDislikedIng = cursor.getString(1)?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
                existingLikedPlat = cursor.getString(2)?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
                existingDislikedPlat = cursor.getString(3)?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            } else {
                existingLikedIng = mutableListOf()
                existingLikedPlat = mutableListOf()
                existingDislikedIng = mutableListOf()
                existingDislikedPlat = mutableListOf()
            }
            cursor.close()

            // Fusionne avec ce qu'on vient de modifier
            val finalLikedIng = if (isLike) likedIngredients else existingLikedIng
            val finalLikedPlat = if (isLike) likedPlats else existingLikedPlat
            val finalDislikedIng = if (!isLike) dislikedIngredients else existingDislikedIng
            val finalDislikedPlat = if (!isLike) dislikedPlats else existingDislikedPlat

            // INSERT OR REPLACE : crée si n'existe pas, écrase si existe
            db.execSQL(
                "INSERT OR REPLACE INTO gouts (id_personne, aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat) VALUES (?, ?, ?, ?, ?)",
                arrayOf(
                    personId,
                    finalLikedIng.joinToString(","),
                    finalDislikedIng.joinToString(","),
                    finalLikedPlat.joinToString(","),
                    finalDislikedPlat.joinToString(",")
                )
            )

            if (isLike) refreshLikesView() else refreshDislikesView()

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}
