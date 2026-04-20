package com.example.gestion_mobilek

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PersonGroupTasteActivity : AppCompatActivity() {

    private enum class TasteFilter {
        COMMON,
        ALL
    }

    private data class TasteBuckets(
        val likedIngredients: MutableSet<String> = mutableSetOf(),
        val likedPlats: MutableSet<String> = mutableSetOf(),
        val dislikedIngredients: MutableSet<String> = mutableSetOf(),
        val dislikedPlats: MutableSet<String> = mutableSetOf()
    )

    private lateinit var dbHelper: DatabaseHelper
    private val selectedPersonIds = mutableListOf<Int>()
    private var likesFilter = TasteFilter.COMMON
    private var dislikesFilter = TasteFilter.COMMON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_group_taste)

        dbHelper = DatabaseHelper(this)

        val ids = intent.getIntArrayExtra("SELECTED_PERSON_IDS")
            ?.toList()
            ?.filter { it > 0 }
            .orEmpty()

        selectedPersonIds.clear()
        selectedPersonIds.addAll(ids)

        if (selectedPersonIds.isEmpty()) {
            Toast.makeText(this, "Aucune personne selectionnee", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnAddFutureTop).setOnClickListener { openAddFutureRecipe() }
        findViewById<ImageButton>(R.id.btnLikesFilter).setOnClickListener { anchor ->
            showFilterMenu(anchor, isLikes = true)
        }
        findViewById<ImageButton>(R.id.btnDislikesFilter).setOnClickListener { anchor ->
            showFilterMenu(anchor, isLikes = false)
        }
        findViewById<Button>(R.id.btnAddFutureBottom).setOnClickListener { openAddFutureRecipe() }
    }

    override fun onResume() {
        super.onResume()
        renderHeaderTitle()
        renderTastes()
    }

    private fun showFilterMenu(anchor: View, isLikes: Boolean) {
        val popup = PopupMenu(this, anchor, Gravity.END)
        popup.menu.add(0, 1, 1, getString(R.string.group_taste_filter_common))
        popup.menu.add(0, 2, 2, getString(R.string.group_taste_filter_all))
        popup.setOnMenuItemClickListener { item ->
            val filter = if (item.itemId == 1) TasteFilter.COMMON else TasteFilter.ALL
            if (isLikes) {
                likesFilter = filter
            } else {
                dislikesFilter = filter
            }
            renderHeaderTitle()
            renderTastes()
            true
        }
        popup.show()
    }

    private fun renderHeaderTitle() {
        val title = findViewById<TextView>(R.id.tvGroupTitle)
        val names = fetchPersonNames(selectedPersonIds)
        if (names.isEmpty()) {
            title.text = getString(R.string.group_taste_title)
            return
        }

        val shown = names.take(3).joinToString(", ")
        title.text = if (names.size > 3) "$shown +" else shown
    }

    private fun renderTastes() {
        val likesContainer = findViewById<LinearLayout>(R.id.containerGroupLikes)
        val dislikesContainer = findViewById<LinearLayout>(R.id.containerGroupDislikes)

        findViewById<TextView>(R.id.tvLikesHeader).text = if (likesFilter == TasteFilter.COMMON) {
            getString(R.string.group_taste_likes_common)
        } else {
            getString(R.string.group_taste_likes_all)
        }
        findViewById<TextView>(R.id.tvDislikesHeader).text = if (dislikesFilter == TasteFilter.COMMON) {
            getString(R.string.group_taste_dislikes_common)
        } else {
            getString(R.string.group_taste_dislikes_all)
        }

        likesContainer.removeAllViews()
        dislikesContainer.removeAllViews()

        try {
            val bucketsByPerson = selectedPersonIds.map { loadPersonTastes(it) }

            val commonLikes = if (bucketsByPerson.isNotEmpty()) {
                val first = bucketsByPerson.first()
                val commonLikedIngredients = first.likedIngredients.toMutableSet()
                val commonLikedPlats = first.likedPlats.toMutableSet()
                for (index in 1 until bucketsByPerson.size) {
                    commonLikedIngredients.retainAll(bucketsByPerson[index].likedIngredients)
                    commonLikedPlats.retainAll(bucketsByPerson[index].likedPlats)
                }
                commonLikedIngredients.sorted().map { "🧄 $it" } + commonLikedPlats.sorted().map { "🍽️ $it" }
            } else {
                emptyList()
            }

            val commonDislikes = if (bucketsByPerson.isNotEmpty()) {
                val first = bucketsByPerson.first()
                val commonDislikedIngredients = first.dislikedIngredients.toMutableSet()
                val commonDislikedPlats = first.dislikedPlats.toMutableSet()
                for (index in 1 until bucketsByPerson.size) {
                    commonDislikedIngredients.retainAll(bucketsByPerson[index].dislikedIngredients)
                    commonDislikedPlats.retainAll(bucketsByPerson[index].dislikedPlats)
                }
                commonDislikedIngredients.sorted().map { "🧄 $it" } + commonDislikedPlats.sorted().map { "🍽️ $it" }
            } else {
                emptyList()
            }

            val allLikes = bucketsByPerson
                .flatMap { it.likedIngredients.sorted().map { item -> "🧄 $item" } + it.likedPlats.sorted().map { item -> "🍽️ $item" } }
                .distinct()

            val allDislikes = bucketsByPerson
                .flatMap { it.dislikedIngredients.sorted().map { item -> "🧄 $item" } + it.dislikedPlats.sorted().map { item -> "🍽️ $item" } }
                .distinct()

            val likesToDisplay = if (likesFilter == TasteFilter.COMMON) commonLikes else allLikes
            val dislikesToDisplay = if (dislikesFilter == TasteFilter.COMMON) commonDislikes else allDislikes

            if (likesToDisplay.isEmpty()) {
                addSimpleText(
                    likesContainer,
                    if (likesFilter == TasteFilter.COMMON) {
                        getString(R.string.group_taste_empty_common_likes)
                    } else {
                        getString(R.string.group_taste_empty_all_likes)
                    }
                )
            } else {
                likesToDisplay.forEach { addSimpleText(likesContainer, it) }
            }

            if (dislikesToDisplay.isEmpty()) {
                addSimpleText(
                    dislikesContainer,
                    if (dislikesFilter == TasteFilter.COMMON) {
                        getString(R.string.group_taste_empty_common_dislikes)
                    } else {
                        getString(R.string.group_taste_empty_all_dislikes)
                    }
                )
            } else {
                dislikesToDisplay.forEach { addSimpleText(dislikesContainer, it) }
            }
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Erreur BDD: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPersonTastes(personId: Int): TasteBuckets {
        val buckets = TasteBuckets()
        val db = dbHelper.getDatabase()
        db.rawQuery(
            "SELECT aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat FROM gouts WHERE id_personne = ?",
            arrayOf(personId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                buckets.likedIngredients.addAll(parseCsv(cursor.getString(0)))
                buckets.dislikedIngredients.addAll(parseCsv(cursor.getString(1)))
                buckets.likedPlats.addAll(parseCsv(cursor.getString(2)))
                buckets.dislikedPlats.addAll(parseCsv(cursor.getString(3)))
            }
        }
        return buckets
    }

    private fun parseCsv(value: String?): List<String> {
        return value
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun fetchPersonNames(ids: List<Int>): List<String> {
        if (ids.isEmpty()) return emptyList()
        return try {
            val db = dbHelper.getDatabase()
            val names = mutableListOf<String>()
            ids.forEach { personId ->
                db.rawQuery("SELECT nom FROM personnes WHERE id = ?", arrayOf(personId.toString())).use { c ->
                    if (c.moveToFirst()) names.add(c.getString(0) ?: "#$personId")
                }
            }
            names
        } catch (_: SQLiteException) {
            emptyList()
        }
    }

    private fun addSimpleText(container: LinearLayout, text: String) {
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

    private fun openAddFutureRecipe() {
        if (selectedPersonIds.isEmpty()) {
            Toast.makeText(this, "Aucune personne selectionnee", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, AddEditFutureRecetteActivity::class.java).apply {
            putExtra("PRESELECTED_PERSON_IDS", selectedPersonIds.toIntArray())
        })
    }
}

