package com.example.gestion_mobilek.guest

import com.example.gestion_mobilek.R
import com.example.gestion_mobilek.app.*
import com.example.gestion_mobilek.data.*
import com.example.gestion_mobilek.reminders.*
import com.example.gestion_mobilek.sync.*
import com.example.gestion_mobilek.ui.common.*
import com.example.gestion_mobilek.ui.future.*
import com.example.gestion_mobilek.ui.history.*
import com.example.gestion_mobilek.ui.items.*
import com.example.gestion_mobilek.ui.main.*
import com.example.gestion_mobilek.ui.persons.*
import com.example.gestion_mobilek.ui.settings.*
import com.example.gestion_mobilek.utils.*

import android.content.Intent
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

class GuestInviteActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var server: GuestHttpServer? = null
    private var currentToken: String? = null
    private var currentUrl: String? = null
    private var personId: Int = -1
    private var personName: String = ""
    private var responseReceived = false

    private lateinit var tvPersonName: TextView
    private lateinit var ivQrCode: ImageView
    private lateinit var tvLink: TextView
    private lateinit var btnShare: Button
    private lateinit var tvServerStatus: TextView
    private lateinit var tvExpiry: TextView
    private lateinit var tvResponseStatus: TextView
    private lateinit var btnMerge: Button
    private lateinit var tvWifiWarning: TextView

    private val timerHandler = Handler(Looper.getMainLooper())
    private var expiresAt: Long = 0L

    private val timerRunnable = object : Runnable {
        override fun run() {
            updateExpiryLabel()
            timerHandler.postDelayed(this, 60_000)
        }
    }

    companion object {
        const val EXTRA_PERSON_ID = "PERSON_ID"
        const val EXTRA_PERSON_NAME = "PERSON_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest_invite)

        personId = intent.getIntExtra(EXTRA_PERSON_ID, -1)
        personName = intent.getStringExtra(EXTRA_PERSON_NAME) ?: ""
        dbHelper = DatabaseHelper(this)

        tvPersonName = findViewById(R.id.tvGuestPersonName)
        ivQrCode = findViewById(R.id.ivQrCode)
        tvLink = findViewById(R.id.tvInviteLink)
        btnShare = findViewById(R.id.btnShareLink)
        tvServerStatus = findViewById(R.id.tvServerStatus)
        tvExpiry = findViewById(R.id.tvExpiry)
        tvResponseStatus = findViewById(R.id.tvResponseStatus)
        btnMerge = findViewById(R.id.btnMergeGuest)
        tvWifiWarning = findViewById(R.id.tvWifiWarning)

        findViewById<ImageButton>(R.id.btnBackGuest).setOnClickListener { finish() }

        tvPersonName.text = if (personName.isNotBlank()) "Invitation pour : $personName" else "Nouvelle invitation"

        btnMerge.visibility = View.GONE
        btnMerge.setOnClickListener { doMerge() }

        btnShare.setOnClickListener {
            currentUrl?.let { url ->
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                startActivity(Intent.createChooser(i, "Partager le lien d'invitation"))
            }
        }

        tvLink.setOnLongClickListener {
            currentUrl?.let { url ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Lien invitation", url))
                Toast.makeText(this, "Lien copié", Toast.LENGTH_SHORT).show()
            }
            true
        }

        generateAndStartServer()
    }

    private fun generateAndStartServer() {
        val ip = GuestInviteManager.getLocalIp(this)
        if (ip == null) {
            tvWifiWarning.visibility = View.VISIBLE
            tvWifiWarning.text = "Wi-Fi requis pour cette fonctionnalité. Connectez-vous au Wi-Fi et relancez."
            tvServerStatus.text = "Serveur arrêté"
            return
        }
        tvWifiWarning.visibility = View.GONE

        val port = SettingsStore.getGuestServerPort(this)
        val db = dbHelper.getDatabase()
        GuestInviteManager.ensureLocalTables(db)

        val token = GuestInviteManager.createInviteToken(
            db,
            if (personId != -1) personId else null,
            personName
        )
        currentToken = token
        expiresAt = System.currentTimeMillis() + GuestInviteManager.EXPIRY_MILLIS
        val url = GuestInviteManager.buildInviteUrl(ip, port, token)
        currentUrl = url

        tvLink.text = url
        generateQrCode(url)

        // Démarre le serveur
        try {
            server = GuestHttpServer(this, port) { receivedToken ->
                if (receivedToken == currentToken) {
                    responseReceived = true
                    runOnUiThread { onResponseReceived() }
                }
            }
            server!!.start()
            tvServerStatus.text = "🟢 En écoute sur le port $port"
        } catch (e: Exception) {
            tvServerStatus.text = "🔴 Erreur démarrage serveur: ${e.message}"
        }

        timerHandler.post(timerRunnable)
        tvResponseStatus.text = "En attente de la réponse de l'invité..."
    }

    private fun generateQrCode(url: String) {
        try {
            val writer = MultiFormatWriter()
            val matrix = writer.encode(url, BarcodeFormat.QR_CODE, 600, 600)
            val encoder = BarcodeEncoder()
            val bitmap: Bitmap = encoder.createBitmap(matrix)
            ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            ivQrCode.visibility = View.GONE
        }
    }

    private fun updateExpiryLabel() {
        val remaining = expiresAt - System.currentTimeMillis()
        if (remaining <= 0) {
            tvExpiry.text = "Lien expiré"
            timerHandler.removeCallbacks(timerRunnable)
            return
        }
        val hours = remaining / 3_600_000
        val minutes = (remaining % 3_600_000) / 60_000
        tvExpiry.text = "Expire dans ${hours}h ${minutes}min"
    }

    private fun onResponseReceived() {
        tvResponseStatus.text = "Réponse reçue !"
        btnMerge.visibility = View.VISIBLE
    }

    private fun doMerge() {
        val token = currentToken ?: return
        val db = dbHelper.getDatabase()
        db.rawQuery(
            "SELECT id FROM invite_responses WHERE token = ?",
            arrayOf(token)
        ).use { c ->
            if (!c.moveToFirst()) {
                Toast.makeText(this, "Réponse introuvable.", Toast.LENGTH_SHORT).show()
                return
            }
            val responseId = c.getInt(0)
            Thread {
                GuestInviteManager.mergeGuestResponse(db, responseId)
                runOnUiThread {
                    Toast.makeText(this, "Goûts fusionnés !", Toast.LENGTH_SHORT).show()
                    btnMerge.visibility = View.GONE
                    tvResponseStatus.text = "Goûts importés avec succès."
                }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        if (server != null && !server!!.isAlive) {
            try {
                server!!.start()
                val port = SettingsStore.getGuestServerPort(this)
                tvServerStatus.text = "🟢 En écoute sur le port $port"
            } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        server?.stop()
        tvServerStatus.text = "🔴 Serveur arrêté (app en arrière-plan)"
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        server?.stop()
    }
}
