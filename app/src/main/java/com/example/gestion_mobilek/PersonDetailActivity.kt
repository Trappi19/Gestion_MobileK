package com.example.gestion_mobilek

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PersonDetailActivity : AppCompatActivity() {

    private data class TasteOption(
        val isIngredient: Boolean,
        val name: String
    )

    private lateinit var dbHelper: DatabaseHelper
    private var personId: Int = -1
    private lateinit var personName: String

    // Listes en mémoire (depuis BDD)
    private val likedIngredients = mutableListOf<String>()
    private val likedPlats = mutableListOf<String>()
    private val dislikedIngredients = mutableListOf<String>()
    private val dislikedPlats = mutableListOf<String>()
    private var pendingIsLikeForNewItem = true

    companion object {
        private const val REQUEST_ADD_INGREDIENT = 4101
        private const val REQUEST_ADD_PLAT = 4102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_detail)

        personId = intent.getIntExtra("PERSON_ID", -1)
        personName = intent.getStringExtra("PERSON_NAME") ?: "?"
        dbHelper = DatabaseHelper(this)

        findViewById<TextView>(R.id.tvPersonName).text = personName
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEditPerson).setOnClickListener {
            startActivity(Intent(this, EditPersonActivity::class.java).apply {
                putExtra("PERSON_ID", personId)
                putExtra("PERSON_NAME", personName)
            })
        }

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
        pendingIsLikeForNewItem = isLike

        // Récupère ingrédients et plats depuis BDD
        val ingredients = mutableListOf<String>()
        db.rawQuery("SELECT nom_ingredient FROM ingrédient ORDER BY nom_ingredient", null).use { c ->
            if (c.moveToFirst()) do { ingredients.add(c.getString(0)) } while (c.moveToNext())
        }
        val plats = mutableListOf<String>()
        db.rawQuery("SELECT nom_plat FROM plats ORDER BY nom_plat", null).use { c ->
            if (c.moveToFirst()) do { plats.add(c.getString(0)) } while (c.moveToNext())
        }

        if (ingredients.isEmpty() && plats.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (isLike) "Aime" else "N'aime pas")
                .setMessage("Aucun ingrédient ou plat disponible.")
                .setPositiveButton("+ Nouvel ingrédient") { _, _ ->
                    startQuickAddItem("ingredient")
                }
                .setNeutralButton("+ Nouveau plat") { _, _ ->
                    startQuickAddItem("plat")
                }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }

        val currentLikedIng = if (isLike) likedIngredients else dislikedIngredients
        val currentLikedPlat = if (isLike) likedPlats else dislikedPlats

        val options = mutableListOf<TasteOption>()
        options.addAll(ingredients.map { TasteOption(true, it) })
        options.addAll(plats.map { TasteOption(false, it) })

        val initial = options.filter { option ->
            if (option.isIngredient) currentLikedIng.contains(option.name) else currentLikedPlat.contains(option.name)
        }.toSet()

        SearchableMultiSelectDialog.show(
            context = this,
            title = if (isLike) "Aime" else "N'aime pas",
            items = options,
            labelOf = { if (it.isIngredient) "🧄 ${it.name}" else "🍽️ ${it.name}" },
            initialSelection = initial,
            neutralButtonText = "+ Nouveau",
            onNeutral = { showQuickAddChoiceDialog() },
            onConfirm = { selected ->
                currentLikedIng.clear()
                currentLikedPlat.clear()
                selected.forEach { option ->
                    if (option.isIngredient) currentLikedIng.add(option.name) else currentLikedPlat.add(option.name)
                }
                saveGoûtsToDb(isLike)
            }
        )
    }

    private fun showQuickAddChoiceDialog() {
        val options = arrayOf("Nouvel ingrédient", "Nouveau plat")
        android.app.AlertDialog.Builder(this)
            .setTitle("Ajouter rapidement")
            .setItems(options) { _, which ->
                val type = if (which == 0) "ingredient" else "plat"
                startQuickAddItem(type)
            }
            .show()
    }

    private fun startQuickAddItem(type: String) {
        val requestCode = if (type == "ingredient") REQUEST_ADD_INGREDIENT else REQUEST_ADD_PLAT
        startActivityForResult(Intent(this, AddItemActivity::class.java).apply {
            putExtra("TYPE", type)
        }, requestCode)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        val createdItemName = data?.getStringExtra("ITEM_NAME")?.trim().orEmpty()
        if (createdItemName.isBlank()) return

        when (requestCode) {
            REQUEST_ADD_INGREDIENT -> {
                val target = if (pendingIsLikeForNewItem) likedIngredients else dislikedIngredients
                if (!target.contains(createdItemName)) target.add(createdItemName)
                saveGoûtsToDb(pendingIsLikeForNewItem)
                showPickerDialog(pendingIsLikeForNewItem)
            }

            REQUEST_ADD_PLAT -> {
                val target = if (pendingIsLikeForNewItem) likedPlats else dislikedPlats
                if (!target.contains(createdItemName)) target.add(createdItemName)
                saveGoûtsToDb(pendingIsLikeForNewItem)
                showPickerDialog(pendingIsLikeForNewItem)
            }
        }
    }

}
