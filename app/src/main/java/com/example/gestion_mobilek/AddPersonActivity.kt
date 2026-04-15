package com.example.gestion_mobilek

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class AddPersonActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var selectedDateStorage: String? = null

    // Listes cochées (noms complets séparés par virgule pour la BDD)
    private val likedIngredients = mutableListOf<String>()
    private val likedPlats = mutableListOf<String>()
    private val dislikedIngredients = mutableListOf<String>()
    private val dislikedPlats = mutableListOf<String>()

    companion object {
        const val REQUEST_ADD_INGREDIENT = 1001
        const val REQUEST_ADD_PLAT = 1002
        // Tags pour savoir quel container on remplit
        var pendingContainer: String = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_person)

        dbHelper = DatabaseHelper(this)

        val etName = findViewById<EditText>(R.id.etName)
        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        val tvDate = findViewById<TextView>(R.id.tvSelectedDate)
        val btnConfirm = findViewById<Button>(R.id.btnConfirmAdd)

        findViewById<ImageButton>(R.id.btnBackAdd).setOnClickListener { finish() }

        // Calendrier
        btnPickDate.setOnClickListener {
            val today = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                selectedDateStorage = DateStorageUtils.toStorageDate(d, m, y)
                tvDate.text = "$d/${m + 1}/$y"
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Boutons "+" pour chaque section
        findViewById<Button>(R.id.btnAddLikeIngredient).setOnClickListener {
            showPickerDialog("ingredient", likedIngredients, R.id.containerLikesIngredients)
        }
        findViewById<Button>(R.id.btnAddLikePlat).setOnClickListener {
            showPickerDialog("plat", likedPlats, R.id.containerLikesPlats)
        }
        findViewById<Button>(R.id.btnAddDislikeIngredient).setOnClickListener {
            showPickerDialog("ingredient", dislikedIngredients, R.id.containerDislikesIngredients)
        }
        findViewById<Button>(R.id.btnAddDislikePlat).setOnClickListener {
            showPickerDialog("plat", dislikedPlats, R.id.containerDislikesPlats)
        }

        // Validation finale
        btnConfirm.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(this, "Entrez un nom", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (selectedDateStorage == null) { Toast.makeText(this, "Choisissez une date", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            try {
                val db = dbHelper.getDatabase()

                // INSERT personne
                val personValues = ContentValues().apply {
                    put("nom", name)
                    put("dernier_passage", selectedDateStorage)
                }
                val personId = db.insert("personnes", null, personValues)

                if (personId == -1L) {
                    Toast.makeText(this, "Erreur ajout personne", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // INSERT gouts
                val goutValues = ContentValues().apply {
                    put("id_personne", personId.toInt())
                    put("aime_ingredient", likedIngredients.joinToString(","))
                    put("aime_pas_ingredient", dislikedIngredients.joinToString(","))
                    put("aime_plat", likedPlats.joinToString(","))
                    put("aime_pas_plat", dislikedPlats.joinToString(","))
                }
                db.insert("gouts", null, goutValues)

                Toast.makeText(this, "$name ajouté !", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK, Intent().apply {
                    putExtra("PERSON_ID", personId.toInt())
                    putExtra("PERSON_NAME", name)
                })
                finish()
            } catch (e: SQLiteException) {
                Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Dialog avec liste cochable + bouton "Nouveau"
    private fun showPickerDialog(type: String, selectedList: MutableList<String>, containerId: Int) {
        val db = dbHelper.getDatabase()
        val query = if (type == "ingredient") "SELECT nom_ingredient FROM ingrédient ORDER BY nom_ingredient"
        else "SELECT nom_plat FROM plats ORDER BY nom_plat"

        val items = mutableListOf<String>()
        val cursor = db.rawQuery(query, null)
        if (cursor.moveToFirst()) do { items.add(cursor.getString(0)) } while (cursor.moveToNext())
        cursor.close()

        if (items.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (type == "ingredient") "Ingrédients" else "Plats")
                .setMessage("Aucun élément disponible")
                .setPositiveButton("+ Nouveau") { _, _ ->
                    pendingContainer = containerId.toString()
                    val intent = Intent(this, AddItemActivity::class.java)
                    intent.putExtra("TYPE", type)
                    startActivityForResult(intent, if (type == "ingredient") REQUEST_ADD_INGREDIENT else REQUEST_ADD_PLAT)
                }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }

        SearchableMultiSelectDialog.show(
            context = this,
            title = if (type == "ingredient") "Ingrédients" else "Plats",
            items = items,
            labelOf = { it },
            initialSelection = selectedList.toSet(),
            neutralButtonText = "+ Nouveau",
            onNeutral = {
                pendingContainer = containerId.toString()
                val intent = Intent(this, AddItemActivity::class.java)
                intent.putExtra("TYPE", type)
                startActivityForResult(intent, if (type == "ingredient") REQUEST_ADD_INGREDIENT else REQUEST_ADD_PLAT)
            },
            onConfirm = { selected ->
                selectedList.clear()
                selectedList.addAll(items.filter { selected.contains(it) })
                refreshCheckedView(containerId, selectedList)
            }
        )
    }

    // Met à jour l'affichage des items cochés sous chaque section
    private fun refreshCheckedView(containerId: Int, list: List<String>) {
        val container = findViewById<LinearLayout>(containerId)
        container.removeAllViews()
        list.forEach { item ->
            val tv = TextView(this)
            tv.text = "• $item"
            tv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            container.addView(tv)
        }
    }

    // Retour depuis AddItemActivity : rouvre le dialog automatiquement
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val containerId = pendingContainer.toIntOrNull() ?: return
            val createdItemName = data?.getStringExtra("ITEM_NAME")?.trim().orEmpty()
            when (requestCode) {
                REQUEST_ADD_INGREDIENT -> {
                    val targetList = if (containerId == R.id.containerLikesIngredients) likedIngredients else dislikedIngredients
                    if (createdItemName.isNotBlank() && !targetList.contains(createdItemName)) {
                        targetList.add(createdItemName)
                    }
                    showPickerDialog("ingredient", targetList, containerId)
                }

                REQUEST_ADD_PLAT -> {
                    val targetList = if (containerId == R.id.containerLikesPlats) likedPlats else dislikedPlats
                    if (createdItemName.isNotBlank() && !targetList.contains(createdItemName)) {
                        targetList.add(createdItemName)
                    }
                    showPickerDialog("plat", targetList, containerId)
                }
            }
        }
    }
}
