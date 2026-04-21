package com.example.gestion_mobilek

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.gestion_mobilek.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    @Volatile
    private var connectionBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4201)
        }

        dbHelper = DatabaseHelper(this)

        setupBottomSheet()

        binding.btnPlats.setOnClickListener {
            startActivity(Intent(this, ItemListActivity::class.java).apply {
                putExtra("TYPE", "plat")
            })
        }
        binding.btnPersonnes.setOnClickListener {
            startActivity(Intent(this, PersonListActivity::class.java))
        }
        binding.btnIngredients.setOnClickListener {
            startActivity(Intent(this, ItemListActivity::class.java).apply {
                putExtra("TYPE", "ingredient")
            })
        }
        binding.btnFutureRecettes.setOnClickListener {
            startActivity(Intent(this, FutureRecettesActivity::class.java))
        }
        binding.btnHistorique.setOnClickListener {
            startActivity(Intent(this, HistoriqueActivity::class.java))
        }
        binding.btnMenu.setOnClickListener {
            PopupMenu(this, binding.btnMenu).apply {
                this@MainActivity.menuInflater.inflate(R.menu.main_activity_menu, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_settings -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            true
                        }
                        R.id.action_license -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java).apply {
                                putExtra("OPEN_LICENSE", true)
                            })
                            true
                        }
                        R.id.action_about -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java).apply {
                                putExtra("OPEN_ABOUT", true)
                            })
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        binding.btnConnectExternal.setOnClickListener {
            if (connectionBusy) return@setOnClickListener
            if (SettingsStore.isExternalDataSourceEnabled(this)) {
                switchBackToLocal()
            } else {
                connectToExternalSource()
            }
        }

        refreshDataSourceUi()

        if (SettingsStore.shouldKeepExternalMode(this) && !SettingsStore.isExternalDataSourceEnabled(this)) {
            connectToExternalSource(auto = true)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            FutureRecettesManager.migrateDueFutureRepas(this, dbHelper.getDatabase())
            FutureReminderScheduler.rescheduleAll(this)
        } catch (_: SQLiteException) {
        }
        refreshDataSourceUi()
        if (!connectionBusy && SettingsStore.shouldKeepExternalMode(this) && !SettingsStore.isExternalDataSourceEnabled(this)) {
            connectToExternalSource(auto = true)
        }
        binding.containerLastMeals.post {
            loadLastMeals()
        }
    }

    private fun setConnectionBusy(busy: Boolean) {
        connectionBusy = busy
        setHomeButtonsEnabled(!busy)
        if (busy) {
            binding.btnConnectExternal.text = getString(R.string.home_connecting)
        } else {
            refreshDataSourceUi()
        }
    }

    private fun setHomeButtonsEnabled(enabled: Boolean) {
        val views = listOf(
            binding.btnMenu,
            binding.btnIngredients,
            binding.btnFutureRecettes,
            binding.btnPersonnes,
            binding.btnPlats,
            binding.btnHistorique,
            binding.btnConnectExternal
        )
        views.forEach { view ->
            view.isEnabled = enabled
            view.alpha = if (enabled) 1f else 0.45f
        }
        binding.bottomSheet.isEnabled = enabled
        binding.bottomSheet.isClickable = enabled
    }

    private fun refreshDataSourceUi() {
        val external = SettingsStore.isExternalDataSourceEnabled(this)
        if (external) {
            binding.btnConnectExternal.text = getString(R.string.home_disconnect_external)
            val dbName = SettingsStore.getExternalDatabaseName(this) ?: "?"
            binding.tvDataSourceStatus.text = getString(R.string.home_data_source_external, dbName)
        } else {
            binding.btnConnectExternal.text = getString(R.string.home_connect_external)
            binding.tvDataSourceStatus.text = getString(R.string.home_data_source_local)
        }
    }

    private fun connectToExternalSource(auto: Boolean = false) {
        if (connectionBusy) return
        setConnectionBusy(true)
        Thread {
            val result = ExternalMariaDbSync.connectAndPull(applicationContext)
            runOnUiThread {
                setConnectionBusy(false)
                result
                    .onSuccess { dbName ->
                        if (!auto) {
                            Toast.makeText(this, getString(R.string.home_external_connected, dbName), Toast.LENGTH_LONG).show()
                        }
                        loadLastMeals()
                    }
                    .onFailure { e ->
                        if (!auto) {
                            Toast.makeText(this, getString(R.string.home_external_connection_failed, e.message ?: "?"), Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }.start()
    }

    private fun switchBackToLocal() {
        setConnectionBusy(true)
        Thread {
            val pushResult = ExternalMariaDbSync.pushExternalToRemote(applicationContext)
            SettingsStore.setExternalDataSourceEnabled(applicationContext, false)
            SettingsStore.setKeepExternalMode(applicationContext, false)
            DatabaseHelper.closeActiveDatabase()
            runOnUiThread {
                setConnectionBusy(false)
                pushResult
                    .onSuccess {
                        Toast.makeText(this, getString(R.string.home_returned_local), Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(this, getString(R.string.home_returned_local_unsynced), Toast.LENGTH_LONG).show()
                    }
                loadLastMeals()
            }
        }.start()
    }

    // ─── BOTTOM SHEET ───────────────────────────────────────────────

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.peekHeight = dpToPx(80)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isHideable = false

        val screenHeight = resources.displayMetrics.heightPixels
        val maxHeight = screenHeight / 2

        binding.bottomSheet.post {
            val params = binding.bottomSheet.layoutParams
            params.height = maxHeight
            binding.bottomSheet.layoutParams = params
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun updateStatsFromDB() {
        try {
            dbHelper.getDatabase()
        } catch (_: SQLiteException) { /* silencieux */ }
    }

    // ─── 3 DERNIERS REPAS ───────────────────────────────────────────

    private fun loadLastMeals() {
        binding.containerLastMeals.removeAllViews()
        try {
            val db = dbHelper.getDatabase()
            val dateConfig = RepasDateCompat.resolve(db)
            val cursor = if (dateConfig.isStorageDate) {
                val todayStorage = DateStorageUtils.todayStorageDate()
                val todaySortable = DateStorageUtils.toSortable(todayStorage) ?: "20260416"
                val orderExpr = "SUBSTR(${dateConfig.columnName}, 5) || SUBSTR(${dateConfig.columnName}, 3, 2) || SUBSTR(${dateConfig.columnName}, 1, 2)"
                db.rawQuery(
                    """SELECT id, nom_plat, id_personnes, ${dateConfig.columnName}
                       FROM repas
                       WHERE ${dateConfig.columnName} IS NOT NULL AND TRIM(${dateConfig.columnName}) != ''
                         AND $orderExpr < ?
                       ORDER BY $orderExpr DESC
                       LIMIT 3""",
                    arrayOf(todaySortable)
                )
            } else {
                db.rawQuery(
                    """SELECT id, nom_plat, id_personnes, ${dateConfig.columnName}
                       FROM repas
                       WHERE ${dateConfig.columnName} > 0
                       ORDER BY ${dateConfig.columnName} ASC
                       LIMIT 3""",
                    null
                )
            }
            if (cursor.moveToFirst()) {
                do {
                    addLastMealRow(
                        cursor.getInt(0),
                        cursor.getString(1) ?: "",
                        cursor.getString(2) ?: "",
                        RepasDateCompat.cursorDateAsStorage(dateConfig, cursor.getString(3))
                    )
                } while (cursor.moveToNext())
            } else {
                val tv = TextView(this)
                tv.text = "Aucun repas passé"
                tv.textSize = 14f
                tv.setTextColor(0xFFAAAAAA.toInt())
                tv.setPadding(0, 8, 0, 8)
                binding.containerLastMeals.addView(tv)
            }
            cursor.close()
        } catch (e: SQLiteException) { /* silencieux */ }
    }

    private fun addLastMealRow(
        repasId: Int,
        nomPlat: String,
        idPersonnes: String,
        dateDernierRepas: String?
    ) {
        val dateStr = DateStorageUtils.displayFromStorage(dateDernierRepas)

        // Plats CSV : max 2 affichés
        val platsList = nomPlat.split(",").filter { it.isNotBlank() }
        val platsText = when {
            platsList.isEmpty() -> "Aucun plat"
            platsList.size <= 2 -> platsList.joinToString(", ")
            else -> "${platsList[0]}, ${platsList[1]}..."
        }

        // Personnes : max 2 affichées
        val nomsPersonnes = getPersonNames(idPersonnes)
        val noms = nomsPersonnes.split(", ").filter { it.isNotBlank() }
        val personnesText = when {
            noms.isEmpty() -> "Aucune personne"
            noms.size <= 2 -> noms.joinToString(", ")
            else -> "${noms[0]}, ${noms[1]} +${noms.size - 2}"
        }

        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 4; bottomMargin = 4 }
        btn.textSize = 13f
        btn.textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_START
        btn.text = "🍽️ $platsText\n👥 $personnesText\n📅 $dateStr"
        btn.setTextColor(0xFFFFFFFF.toInt())
        btn.setBackgroundColor(0xFF2A2A2A.toInt())
        btn.setOnClickListener {
            startActivity(Intent(this, HistoriqueActivity::class.java))
        }
        binding.containerLastMeals.addView(btn)
    }


    private fun getPersonNames(idPersonnes: String): String {
        if (idPersonnes.isBlank()) return ""
        return try {
            val db = dbHelper.getDatabase()
            val ids = idPersonnes.split(",").filter { it.isNotBlank() }
            val noms = mutableListOf<String>()
            ids.forEach { id ->
                val c = db.rawQuery(
                    "SELECT nom FROM personnes WHERE id = ?",
                    arrayOf(id.trim())
                )
                if (c.moveToFirst()) noms.add(c.getString(0))
                c.close()
            }
            noms.joinToString(", ")
        } catch (e: SQLiteException) { "?" }
    }
}
