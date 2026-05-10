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
import android.os.Handler
import android.os.Looper
import fi.iki.elonen.NanoHTTPD

class GuestHttpServer(
    private val context: Context,
    port: Int,
    private val onResponseReceived: (tokenId: String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val parts = uri.split("/").filter { it.isNotBlank() }

        // GET /invite/<token> ou POST /invite/<token>
        if (parts.size >= 2 && parts[0] == "invite") {
            val token = parts[1]
            return when {
                parts.size == 3 && parts[2] == "done" -> serveDonePage()
                session.method == Method.POST -> handlePost(session, token)
                else -> serveForm(token)
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
    }

    private fun getTokenRow(db: SQLiteDatabase, token: String): Triple<Int?, String?, Long>? {
        db.rawQuery(
            "SELECT id_personne, nom_invite, expires_at, used FROM invite_tokens WHERE token = ?",
            arrayOf(token)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val used = c.getInt(3)
            if (used == 1) return Triple(-1, null, 0L) // sentinel: déjà utilisé
            val idPersonne = if (c.isNull(0)) null else c.getInt(0)
            val nom = c.getString(1)
            val exp = c.getLong(2)
            return Triple(idPersonne, nom, exp)
        }
    }

    private fun serveForm(token: String): Response {
        val db = DatabaseHelper(context).getDatabase()
        val row = getTokenRow(db, token)

        if (row == null) {
            return htmlResponse(pageError("Lien invalide", "Ce lien d'invitation n'existe pas."))
        }
        val (_, nomInvite, expiresAt) = row
        if (expiresAt == 0L) {
            return htmlResponse(pageError("Déjà soumis", "Vous avez déjà soumis vos préférences. Merci !"))
        }
        if (System.currentTimeMillis() > expiresAt) {
            return htmlResponse(pageError("Lien expiré", "Ce lien a expiré. Demandez un nouveau lien à l'hôte."))
        }

        val ingredients = mutableListOf<String>()
        db.rawQuery("SELECT nom_ingredient FROM `ingrédient` ORDER BY nom_ingredient", null).use { c ->
            if (c.moveToFirst()) do { ingredients.add(c.getString(0)) } while (c.moveToNext())
        }
        val plats = mutableListOf<String>()
        db.rawQuery("SELECT nom_plat FROM plats ORDER BY nom_plat", null).use { c ->
            if (c.moveToFirst()) do { plats.add(c.getString(0)) } while (c.moveToNext())
        }

        val html = buildFormHtml(token, nomInvite, ingredients, plats)
        return htmlResponse(html)
    }

    private fun handlePost(session: IHTTPSession, token: String): Response {
        val db = DatabaseHelper(context).getDatabase()
        val row = getTokenRow(db, token)

        if (row == null) return htmlResponse(pageError("Lien invalide", "Ce lien d'invitation n'existe pas."))
        val (_, _, expiresAt) = row
        if (expiresAt == 0L) return htmlResponse(pageError("Déjà soumis", "Vous avez déjà soumis vos préférences. Merci !"))
        if (System.currentTimeMillis() > expiresAt) return htmlResponse(pageError("Lien expiré", "Ce lien a expiré."))

        // Parse body
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val params = session.parameters

        fun csvFromParams(key: String): String =
            params[key]?.joinToString(",")?.trim(',') ?: ""

        val aimeIng = csvFromParams("aime_ingredient")
        val aimePasIng = csvFromParams("aime_pas_ingredient")
        val aimePlat = csvFromParams("aime_plat")
        val aimePasPlat = csvFromParams("aime_pas_plat")

        synchronized(db) {
            db.beginTransaction()
            try {
                val cv = ContentValues().apply {
                    put("token", token)
                    put("aime_ingredient", aimeIng)
                    put("aime_pas_ingredient", aimePasIng)
                    put("aime_plat", aimePlat)
                    put("aime_pas_plat", aimePasPlat)
                    put("submitted_at", System.currentTimeMillis())
                }
                db.insert("invite_responses", null, cv)
                db.execSQL("UPDATE invite_tokens SET used = 1 WHERE token = ?", arrayOf(token))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        Handler(Looper.getMainLooper()).post { onResponseReceived(token) }

        return newFixedLengthResponse(
            Response.Status.FOUND, MIME_PLAINTEXT, ""
        ).also { it.addHeader("Location", "/invite/$token/done") }
    }

    private fun serveDonePage(): Response {
        return htmlResponse(pageError("Merci !", "Vos préférences ont bien été enregistrées. Vous pouvez fermer cette page."))
    }

    private fun htmlResponse(html: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)

    private fun pageError(title: String, message: String): String = """
        <!DOCTYPE html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title>
        <style>body{font-family:sans-serif;max-width:480px;margin:40px auto;padding:16px;text-align:center}
        h1{color:#4A90E2}.msg{margin-top:16px;font-size:1.1em;color:#555}</style>
        </head><body><h1>$title</h1><p class="msg">$message</p></body></html>
    """.trimIndent()

    private fun buildFormHtml(
        token: String,
        nomInvite: String?,
        ingredients: List<String>,
        plats: List<String>
    ): String {
        fun checkboxes(items: List<String>, name: String) = items.joinToString("\n") { item ->
            val safe = item.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
            """<label class="cb-item"><input type="checkbox" name="$name" value="$safe"> $safe</label>"""
        }

        return """
<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Mes préférences alimentaires</title>
  <style>
    *{box-sizing:border-box}
    body{font-family:sans-serif;background:#F5F5F5;margin:0;padding:16px;color:#333}
    h1{color:#4A90E2;font-size:1.4em;margin-bottom:4px}
    p.sub{color:#777;margin-top:0;margin-bottom:20px}
    .section{background:#fff;border-radius:8px;padding:12px 16px;margin-bottom:16px;box-shadow:0 1px 3px rgba(0,0,0,.12)}
    .section h2{font-size:1em;margin:0 0 10px;padding-bottom:6px;border-bottom:2px solid #eee}
    .section.like h2{border-color:#4CAF50;color:#388E3C}
    .section.dislike h2{border-color:#F44336;color:#C62828}
    .cb-item{display:block;padding:6px 4px;font-size:.95em;cursor:pointer}
    .cb-item input{margin-right:8px}
    .empty{color:#aaa;font-style:italic;font-size:.9em}
    button[type=submit]{width:100%;padding:14px;background:#4A90E2;color:#fff;border:none;border-radius:8px;font-size:1.1em;cursor:pointer;margin-top:8px}
    button[type=submit]:hover{background:#357ABD}
  </style>
</head>
<body>
  <h1>Mes préférences alimentaires</h1>
  <p class="sub">${if (nomInvite != null) "Bonjour <strong>${nomInvite.replace("&","&amp;").replace("<","&lt;")}</strong> !" else "Renseignez vos goûts ci-dessous."}</p>
  <form method="POST" action="/invite/$token">

    <div class="section like">
      <h2>✅ Ingrédients que j'aime</h2>
      ${if (ingredients.isEmpty()) "<p class='empty'>Aucun ingrédient disponible</p>" else checkboxes(ingredients, "aime_ingredient")}
    </div>

    <div class="section dislike">
      <h2>🚫 Ingrédients que je n'aime pas</h2>
      ${if (ingredients.isEmpty()) "<p class='empty'>Aucun ingrédient disponible</p>" else checkboxes(ingredients, "aime_pas_ingredient")}
    </div>

    <div class="section like">
      <h2>✅ Plats que j'aime</h2>
      ${if (plats.isEmpty()) "<p class='empty'>Aucun plat disponible</p>" else checkboxes(plats, "aime_plat")}
    </div>

    <div class="section dislike">
      <h2>🚫 Plats que je n'aime pas</h2>
      ${if (plats.isEmpty()) "<p class='empty'>Aucun plat disponible</p>" else checkboxes(plats, "aime_pas_plat")}
    </div>

    <button type="submit">Envoyer mes préférences</button>
  </form>
</body>
</html>
        """.trimIndent()
    }
}
