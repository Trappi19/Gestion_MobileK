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

class EditPersonActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var personId: Int = -1
    private var selectedDateStorage: String? = null

    private val likedIngredients = mutableListOf<String>()
    private val likedPlats = mutableListOf<String>()
    private val dislikedIngredients = mutableListOf<String>()
    private val dislikedPlats = mutableListOf<String>()

    companion object {
        const val REQUEST_ADD_INGREDIENT = 2001
        const val REQUEST_ADD_PLAT = 2002
        var pendingContainer: String = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_person)  // Réutilise le même XML !

        personId = intent.getIntExtra("PERSON_ID", -1)
        dbHelper = DatabaseHelper(this)

        // Change le titre du bandeau
        // (activity_add_person.xml a un TextView centré sans id fixe, on le cherche)
        // Si tu veux, ajoute android:id="@+id/tvAddTitle" dans activity_add_person.xml
        // Pour l'instant on passe

        findViewById<ImageButton>(R.id.btnBackAdd).setOnClickListener { finish() }

        // Pré-charge les données
        loadExistingData()

        // Calendrier
        findViewById<Button>(R.id.btnPickDate).setOnClickListener {
            val today = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                selectedDateStorage = DateStorageUtils.toStorageDate(d, m, y)
                findViewById<TextView>(R.id.tvSelectedDate).text = "$d/${m + 1}/$y"
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Boutons goûts
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

        // Bouton confirmer → UPDATE
        findViewById<Button>(R.id.btnConfirmAdd).text = "Sauvegarder"
        findViewById<Button>(R.id.btnConfirmAdd).setOnClickListener {
            saveChanges()
        }
    }

    private fun loadExistingData() {
        try {
            val db = dbHelper.getDatabase()

            // Charge personne
            val cp = db.rawQuery("SELECT nom, dernier_passage FROM personnes WHERE id = ?", arrayOf(personId.toString()))
            if (cp.moveToFirst()) {
                findViewById<EditText>(R.id.etName).setText(cp.getString(0))
                selectedDateStorage = DateStorageUtils.normalizeStorageDate(cp.getString(1))
                findViewById<TextView>(R.id.tvSelectedDate).text = DateStorageUtils.displayFromStorage(selectedDateStorage)
            }
            cp.close()

            // Charge goûts
            val cg = db.rawQuery(
                "SELECT aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat FROM gouts WHERE id_personne = ?",
                arrayOf(personId.toString())
            )
            if (cg.moveToFirst()) {
                cg.getString(0)?.split(",")?.filter { it.isNotBlank() }?.let { likedIngredients.addAll(it) }
                cg.getString(1)?.split(",")?.filter { it.isNotBlank() }?.let { dislikedIngredients.addAll(it) }
                cg.getString(2)?.split(",")?.filter { it.isNotBlank() }?.let { likedPlats.addAll(it) }
                cg.getString(3)?.split(",")?.filter { it.isNotBlank() }?.let { dislikedPlats.addAll(it) }
            }
            cg.close()

            // Affiche goûts pré-remplis
            refreshCheckedView(R.id.containerLikesIngredients, likedIngredients)
            refreshCheckedView(R.id.containerLikesPlats, likedPlats)
            refreshCheckedView(R.id.containerDislikesIngredients, dislikedIngredients)
            refreshCheckedView(R.id.containerDislikesPlats, dislikedPlats)

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur chargement: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveChanges() {
        val name = findViewById<EditText>(R.id.etName).text.toString().trim()
        if (name.isEmpty()) { Toast.makeText(this, "Entrez un nom", Toast.LENGTH_SHORT).show(); return }
        if (selectedDateStorage == null) { Toast.makeText(this, "Choisissez une date", Toast.LENGTH_SHORT).show(); return }

        try {
            val db = dbHelper.getDatabase()

            // UPDATE personne
            val pv = ContentValues().apply {
                put("nom", name)
                put("dernier_passage", selectedDateStorage)
            }
            db.update("personnes", pv, "id = ?", arrayOf(personId.toString()))

            // INSERT OR REPLACE goûts
            db.execSQL(
                "INSERT OR REPLACE INTO gouts (id_personne, aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat) VALUES (?, ?, ?, ?, ?)",
                arrayOf(
                    personId,
                    likedIngredients.joinToString(","),
                    dislikedIngredients.joinToString(","),
                    likedPlats.joinToString(","),
                    dislikedPlats.joinToString(",")
                )
            )

            Toast.makeText(this, "$name mis à jour !", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPickerDialog(type: String, selectedList: MutableList<String>, containerId: Int) {
        val db = dbHelper.getDatabase()
        val query = if (type == "ingredient") "SELECT nom_ingredient FROM ingrédient ORDER BY nom_ingredient"
        else "SELECT nom_plat FROM plats ORDER BY nom_plat"
        val items = mutableListOf<String>()
        db.rawQuery(query, null).use { c -> if (c.moveToFirst()) do { items.add(c.getString(0)) } while (c.moveToNext()) }
        if (items.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (type == "ingredient") "Ingrédients" else "Plats")
                .setMessage("Aucun élément disponible")
                .setPositiveButton("+ Nouveau") { _, _ ->
                    pendingContainer = containerId.toString()
                    startActivityForResult(
                        Intent(this, AddItemActivity::class.java).apply { putExtra("TYPE", type) },
                        if (type == "ingredient") REQUEST_ADD_INGREDIENT else REQUEST_ADD_PLAT
                    )
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
                startActivityForResult(
                    Intent(this, AddItemActivity::class.java).apply { putExtra("TYPE", type) },
                    if (type == "ingredient") REQUEST_ADD_INGREDIENT else REQUEST_ADD_PLAT
                )
            },
            onConfirm = { selected ->
                selectedList.clear()
                selectedList.addAll(items.filter { selected.contains(it) })
                refreshCheckedView(containerId, selectedList)
            }
        )
    }

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
