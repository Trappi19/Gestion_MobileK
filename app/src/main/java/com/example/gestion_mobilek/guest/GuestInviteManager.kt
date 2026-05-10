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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.wifi.WifiManager
import java.util.UUID

object GuestInviteManager {

    const val DEFAULT_PORT = 8765
    const val EXPIRY_MILLIS = 48L * 3600 * 1000

    /** Crée les tables locale si elles n'existent pas encore. */
    fun ensureLocalTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS invite_tokens (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                token TEXT NOT NULL UNIQUE,
                id_personne INTEGER,
                nom_invite TEXT,
                expires_at INTEGER NOT NULL,
                used INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS invite_responses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                token TEXT NOT NULL,
                aime_ingredient TEXT,
                aime_pas_ingredient TEXT,
                aime_plat TEXT,
                aime_pas_plat TEXT,
                submitted_at INTEGER NOT NULL
            )"""
        )
    }

    /** Génère un token et l'insère en base locale. Retourne le token UUID. */
    fun createInviteToken(
        db: SQLiteDatabase,
        personId: Int?,
        nomInvite: String
    ): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("token", token)
            if (personId != null) put("id_personne", personId) else putNull("id_personne")
            put("nom_invite", nomInvite)
            put("expires_at", now + EXPIRY_MILLIS)
            put("used", 0)
            put("created_at", now)
        }
        db.insert("invite_tokens", null, cv)
        return token
    }

    /** Récupère l'IP locale Wi-Fi du téléphone. Null si pas de Wi-Fi. */
    fun getLocalIp(context: Context): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        val info = wm.connectionInfo ?: return null
        val raw = info.ipAddress
        if (raw == 0) return null
        return String.format(
            "%d.%d.%d.%d",
            raw and 0xff,
            raw shr 8 and 0xff,
            raw shr 16 and 0xff,
            raw shr 24 and 0xff
        )
    }

    /** Construit l'URL d'invitation. */
    fun buildInviteUrl(ip: String, port: Int, token: String) =
        "http://$ip:$port/invite/$token"

    /** Nombre de réponses en attente (non encore fusionnées). */
    fun pendingResponseCount(db: SQLiteDatabase): Int {
        db.rawQuery("SELECT COUNT(*) FROM invite_responses", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /**
     * Fusionne une invite_response dans la table gouts.
     * Crée la personne si id_personne est null.
     * Union des listes, pas de doublons, pas d'écrasement.
     * Supprime la réponse après traitement.
     */
    fun mergeGuestResponse(db: SQLiteDatabase, responseId: Int) {
        data class Response(
            val token: String,
            val aimeIng: List<String>,
            val aimePasIng: List<String>,
            val aimePlat: List<String>,
            val aimePasPlat: List<String>
        )

        val resp = db.rawQuery(
            "SELECT token, aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat FROM invite_responses WHERE id = ?",
            arrayOf(responseId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return
            Response(
                token = c.getString(0),
                aimeIng = c.getString(1)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                aimePasIng = c.getString(2)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                aimePlat = c.getString(3)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                aimePasPlat = c.getString(4)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            )
        }

        // Récupère le token pour id_personne / nom_invite
        var personId: Int? = null
        var nomInvite = ""
        db.rawQuery(
            "SELECT id_personne, nom_invite FROM invite_tokens WHERE token = ?",
            arrayOf(resp.token)
        ).use { c ->
            if (c.moveToFirst()) {
                personId = if (c.isNull(0)) null else c.getInt(0)
                nomInvite = c.getString(1) ?: ""
            }
        }

        db.beginTransaction()
        try {
            // Crée la personne si besoin
            val finalPersonId = personId ?: run {
                val cv = ContentValues().apply { put("nom", nomInvite.ifBlank { "Invité" }) }
                db.insert("personnes", null, cv).toInt()
            }

            // Goûts existants
            var exAimeIng = listOf<String>()
            var exAimePasIng = listOf<String>()
            var exAimePlat = listOf<String>()
            var exAimePasPlat = listOf<String>()
            db.rawQuery(
                "SELECT aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat FROM gouts WHERE id_personne = ?",
                arrayOf(finalPersonId.toString())
            ).use { c ->
                if (c.moveToFirst()) {
                    exAimeIng = c.getString(0)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                    exAimePasIng = c.getString(1)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                    exAimePlat = c.getString(2)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                    exAimePasPlat = c.getString(3)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                }
            }

            val mergedAimeIng = (exAimeIng + resp.aimeIng).distinct()
            val mergedAimePasIng = (exAimePasIng + resp.aimePasIng).distinct()
            val mergedAimePlat = (exAimePlat + resp.aimePlat).distinct()
            val mergedAimePasPlat = (exAimePasPlat + resp.aimePasPlat).distinct()

            db.execSQL(
                "INSERT OR REPLACE INTO gouts (id_personne, aime_ingredient, aime_pas_ingredient, aime_plat, aime_pas_plat) VALUES (?,?,?,?,?)",
                arrayOf(
                    finalPersonId,
                    mergedAimeIng.joinToString(","),
                    mergedAimePasIng.joinToString(","),
                    mergedAimePlat.joinToString(","),
                    mergedAimePasPlat.joinToString(",")
                )
            )

            db.delete("invite_responses", "id = ?", arrayOf(responseId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Retourne la liste des réponses en attente : id + nom_invite. */
    fun getPendingResponses(db: SQLiteDatabase): List<Pair<Int, String>> {
        val results = mutableListOf<Pair<Int, String>>()
        db.rawQuery(
            """SELECT r.id, COALESCE(t.nom_invite, 'Inconnu')
               FROM invite_responses r
               LEFT JOIN invite_tokens t ON t.token = r.token
               ORDER BY r.submitted_at DESC""",
            null
        ).use { c ->
            if (c.moveToFirst()) do {
                results.add(c.getInt(0) to c.getString(1))
            } while (c.moveToNext())
        }
        return results
    }
}
