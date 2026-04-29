package com.example.gestion_mobilek.sync

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
import com.example.gestion_mobilek.BuildConfig

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.net.Inet4Address
import java.net.InetAddress
import java.text.Normalizer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object ExternalMariaDbSync {

    private data class ResolvedTable(
        val localTable: String,
        val remoteTable: String
    )

    data class ColumnMapping(
        val localColumn: String,
        val remoteColumn: String
    )

    data class TableDiagnostic(
        val localTable: String,
        val remoteTable: String?,
        val localColumns: List<String>,
        val remoteColumns: List<String>,
        val mappedColumns: List<ColumnMapping>,
        val ignoredLocalColumns: List<String>,
        val ignoredRemoteColumns: List<String>
    )

    data class OnlineDiagnosticReport(
        val host: String,
        val port: Int,
        val configuredDatabase: String?,
        val resolvedDatabase: String,
        val tables: List<TableDiagnostic>,
        val notes: List<String>
    )

    private data class MariaDbConfig(
        val host: String,
        val port: Int,
        val user: String,
        val password: String,
        val forcedDatabase: String?
    )

    private val syncTables = listOf(
        "personnes",
        "gouts",
        "ingrédient",
        "plats",
        "repas",
        "future_repas",
        "future_repas_rappels"
    )
    private val optionalRemoteTables = setOf("future_repas", "future_repas_rappels")
    private val requiredRemoteTables = syncTables.toSet() - optionalRemoteTables
    private val coreTables = setOf("personnes", "gouts", "plats")
    private val tableAliases = mapOf(
        "ingrédient" to listOf("ingredient", "ingredients", "ingrdient"),
        "plats" to listOf("plat"),
        "future_repas" to listOf("future_recette", "future_recettes", "futur_repas", "futurs_repas"),
        "future_repas_rappels" to listOf("future_rappels", "rappels_future_repas", "future_recette_rappels", "rappels_future_recettes")
    )
    private val columnAliases = mapOf(
        "nom_plat" to listOf("nom", "plat"),
        "nom_ingredient" to listOf("nom", "ingredient"),
        "date_dernier_repas" to listOf("date_repas"),
        "date_repas" to listOf("date_dernier_repas"),
        "id_personnes" to listOf("id_personne", "personnes", "ids_personnes")
    )

    private fun escapeIdent(name: String): String = "`" + name.replace("`", "``") + "`"

    private fun isLikelyIpAddress(host: String): Boolean {
        return host.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) || host.contains(":")
    }

    private fun connectionHosts(host: String): List<String> {
        if (isLikelyIpAddress(host)) return listOf(host)
        val resolvedV4 = runCatching {
            InetAddress.getAllByName(host)
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())

        return listOf(host) + resolvedV4
    }

    private fun jdbcUrl(host: String, port: Int, database: String?): String {
        val dbSegment = database?.let { "/${escapeDbPath(it)}" } ?: "/"
        return "jdbc:mariadb://$host:$port$dbSegment?connectTimeout=5000&socketTimeout=10000&sslMode=disable"
    }

    private fun openConnectionWithFallback(config: MariaDbConfig, database: String?): Connection {
        Class.forName("org.mariadb.jdbc.Driver")
        var lastError: SQLException? = null

        connectionHosts(config.host).distinct().forEach { hostCandidate ->
            try {
                return DriverManager.getConnection(
                    jdbcUrl(hostCandidate, config.port, database),
                    config.user,
                    config.password
                )
            } catch (e: SQLException) {
                lastError = e
            }
        }

        throw SQLException(
            "Connexion socket impossible vers ${config.host}:${config.port}. " +
                "Verifiez ouverture du port, acces reseau mobile/Wi-Fi et bind MariaDB.",
            lastError
        )
    }

    sealed class DbCheckResult {
        object NotExists : DbCheckResult()
        data class Exists(val name: String) : DbCheckResult()
    }

    private fun resolveConfig(context: Context): MariaDbConfig {
        val host = SettingsStore.getDbHost(context) ?: BuildConfig.MARIADB_HOST.trim()
        val port = SettingsStore.getDbPort(context) ?: BuildConfig.MARIADB_PORT
        val user = SettingsStore.getDbUser(context) ?: BuildConfig.MARIADB_USER.trim()
        val password = SettingsStore.getDbPassword(context) ?: BuildConfig.MARIADB_PASSWORD
        val forcedDatabase = SettingsStore.getDbNameOverride(context)
            ?: BuildConfig.MARIADB_DATABASE.trim().ifBlank { null }

        if (host.isBlank() || port <= 0 || user.isBlank() || password.isBlank()) {
            throw SQLException(
                "Configuration MariaDB manquante : renseignez les paramètres de connexion dans Paramètres > Base de données distante"
            )
        }

        return MariaDbConfig(
            host = host,
            port = port,
            user = user,
            password = password,
            forcedDatabase = forcedDatabase
        )
    }

    fun checkRemoteDbExists(context: Context): DbCheckResult {
        val config = resolveConfig(context)
        val dbName = config.forcedDatabase
            ?: throw SQLException("Nom de la base non configuré — renseignez le champ \"Base de données\" dans Paramètres")
        openServerConnection(config).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SHOW DATABASES LIKE '${dbName.replace("'", "\\'")}'").use { rs ->
                    return if (rs.next()) DbCheckResult.Exists(dbName) else DbCheckResult.NotExists
                }
            }
        }
    }

    fun initRemoteDatabase(context: Context, dropIfExists: Boolean) {
        val config = resolveConfig(context)
        val dbName = config.forcedDatabase
            ?: throw SQLException("Nom de la base non configuré — renseignez le champ \"Base de données\" dans Paramètres")
        val safeName = dbName.replace("`", "")

        openServerConnection(config).use { conn ->
            conn.createStatement().use { stmt ->
                if (dropIfExists) {
                    stmt.execute("DROP DATABASE IF EXISTS `$safeName`")
                }
                stmt.execute(
                    "CREATE DATABASE IF NOT EXISTS `$safeName` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                )
            }
        }

        openDatabaseConnection(config, dbName).use { conn ->
            conn.autoCommit = false
            try {
                createRemoteTables(conn)
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }

        SettingsStore.setExternalDatabaseName(context, dbName)
    }

    private fun createRemoteTables(conn: Connection) {
        conn.createStatement().use { stmt ->
            listOf(
                """CREATE TABLE IF NOT EXISTS personnes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nom VARCHAR(255) NOT NULL,
                    dernier_passage VARCHAR(20)
                ) CHARACTER SET utf8mb4""",
                """CREATE TABLE IF NOT EXISTS gouts (
                    id_personne INT PRIMARY KEY,
                    aime_ingredient TEXT,
                    aime_pas_ingredient TEXT,
                    aime_plat TEXT,
                    aime_pas_plat TEXT
                ) CHARACTER SET utf8mb4""",
                """CREATE TABLE IF NOT EXISTS `ingrédient` (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nom_ingredient VARCHAR(255) NOT NULL
                ) CHARACTER SET utf8mb4""",
                """CREATE TABLE IF NOT EXISTS plats (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nom_plat VARCHAR(255) NOT NULL
                ) CHARACTER SET utf8mb4""",
                """CREATE TABLE IF NOT EXISTS repas (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nom_plat VARCHAR(255),
                    id_personnes TEXT,
                    date_dernier_repas VARCHAR(20),
                    description TEXT
                ) CHARACTER SET utf8mb4""",
                """CREATE TABLE IF NOT EXISTS future_repas (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nom_plat VARCHAR(255) NOT NULL,
                    id_personnes TEXT,
                    date_dernier_repas VARCHAR(20) NOT NULL,
                    description TEXT
                ) CHARACTER SET utf8mb4""",
                """CREATE TABLE IF NOT EXISTS future_repas_rappels (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    future_id INT NOT NULL,
                    trigger_at_millis BIGINT NOT NULL,
                    enabled TINYINT(1) NOT NULL DEFAULT 1,
                    source_mode INT NOT NULL DEFAULT 0
                ) CHARACTER SET utf8mb4""",
                """CREATE TABLE IF NOT EXISTS _sync_history (
                    table_name VARCHAR(100),
                    pk_val VARCHAR(100),
                    PRIMARY KEY (table_name, pk_val)
                ) CHARACTER SET utf8mb4"""
            ).forEach { stmt.execute(it.trimIndent()) }
        }
    }

    private fun openServerConnection(config: MariaDbConfig): Connection {
        return openConnectionWithFallback(config, database = null)
    }

    private fun openDatabaseConnection(config: MariaDbConfig, databaseName: String): Connection {
        return openConnectionWithFallback(config, database = databaseName)
    }

    private fun escapeDbPath(name: String): String {
        // Path segment escaping is intentionally strict for safety.
        return name.replace("`", "")
    }

    private fun normalizeTableName(name: String): String {
        val nfd = Normalizer.normalize(name, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{Mn}+"), "")
    }

    private fun remoteTableNames(connection: Connection, dbName: String): Set<String> {
        val sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, dbName)
            ps.executeQuery().use { rs ->
                val names = mutableSetOf<String>()
                while (rs.next()) {
                    names.add(rs.getString(1))
                }
                return names
            }
        }
    }

    private fun resolveRemoteTableName(localTable: String, remoteNames: Set<String>): String? {
        if (remoteNames.contains(localTable)) return localTable
        val normalizedLocal = normalizeTableName(localTable)

        remoteNames.firstOrNull { normalizeTableName(it).equals(normalizedLocal, ignoreCase = true) }?.let {
            return it
        }

        val candidates = listOf(localTable) + tableAliases[localTable].orEmpty()
        candidates.forEach { candidate ->
            val normalizedCandidate = normalizeTableName(candidate)
            remoteNames.firstOrNull { normalizeTableName(it).equals(normalizedCandidate, ignoreCase = true) }?.let {
                return it
            }
        }
        return null
    }

    private fun remoteTableColumns(connection: Connection, dbName: String, tableName: String): Set<String> {
        val sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, dbName)
            ps.setString(2, tableName)
            ps.executeQuery().use { rs ->
                val cols = mutableSetOf<String>()
                while (rs.next()) {
                    cols.add(rs.getString(1))
                }
                return cols
            }
        }
    }

    private fun resolveRemoteColumnName(localColumn: String, remoteColumns: Set<String>): String? {
        val candidates = listOf(localColumn) + columnAliases[localColumn].orEmpty()
        candidates.forEach { candidate ->
            if (remoteColumns.contains(candidate)) return candidate
            val normalized = normalizeTableName(candidate)
            remoteColumns.firstOrNull { normalizeTableName(it).equals(normalized, ignoreCase = true) }?.let {
                return it
            }
        }
        return null
    }

    private fun resolveLocalColumnName(remoteColumn: String, localColumns: List<String>): String? {
        if (localColumns.contains(remoteColumn)) return remoteColumn
        localColumns.firstOrNull { localColumn ->
            val localCandidates = listOf(localColumn) + columnAliases[localColumn].orEmpty()
            localCandidates.any { candidate ->
                normalizeTableName(candidate).equals(normalizeTableName(remoteColumn), ignoreCase = true)
            }
        }?.let {
            return it
        }

        val normalized = normalizeTableName(remoteColumn)
        return localColumns.firstOrNull { normalizeTableName(it).equals(normalized, ignoreCase = true) }
    }

    private fun localTableHasData(sqliteDb: SQLiteDatabase, table: String): Boolean {
        return runCatching {
            sqliteDb.rawQuery("SELECT 1 FROM ${escapeIdent(table)} LIMIT 1", null).use { it.moveToFirst() }
        }.getOrDefault(false)
    }

    private fun mapTableDiagnostic(
        sqliteDb: SQLiteDatabase,
        remoteConn: Connection,
        databaseName: String,
        localTable: String,
        remoteTable: String?
    ): TableDiagnostic {
        val localColumns = sqliteTableColumns(sqliteDb, localTable)
        val remoteColumns = if (remoteTable != null) {
            remoteTableColumns(remoteConn, databaseName, remoteTable).toList()
        } else {
            emptyList()
        }

        val mappedColumns = if (remoteTable != null) {
            localColumns.mapNotNull { localColumn ->
                resolveRemoteColumnName(localColumn, remoteColumns.toSet())?.let { remoteColumn ->
                    ColumnMapping(localColumn, remoteColumn)
                }
            }
        } else {
            emptyList()
        }

        val ignoredLocalColumns = localColumns.filter { localColumn ->
            mappedColumns.none { it.localColumn == localColumn }
        }
        val ignoredRemoteColumns = remoteColumns.filter { remoteColumn ->
            mappedColumns.none { it.remoteColumn == remoteColumn }
        }

        return TableDiagnostic(
            localTable = localTable,
            remoteTable = remoteTable,
            localColumns = localColumns,
            remoteColumns = remoteColumns,
            mappedColumns = mappedColumns,
            ignoredLocalColumns = ignoredLocalColumns,
            ignoredRemoteColumns = ignoredRemoteColumns
        )
    }

    fun buildOnlineDiagnostic(context: Context): Result<OnlineDiagnosticReport> {
        return runCatching {
            val config = resolveConfig(context)
            openServerConnection(config).use { serverConn ->
                val resolvedDatabase = resolveDatabaseName(context, config, serverConn)
                openDatabaseConnection(config, resolvedDatabase).use { dbConn ->
                    val sqliteDb = DatabaseHelper(context).getDatabaseForMode(useExternal = true)
                    FutureRecettesManager.ensureSchema(sqliteDb)

                    val remoteNames = remoteTableNames(dbConn, resolvedDatabase)
                    val notes = mutableListOf<String>()
                    if (config.forcedDatabase != null) {
                        notes.add("Base forcee via .env : ${config.forcedDatabase}")
                    } else {
                        notes.add("Base distante detectee automatiquement : $resolvedDatabase")
                    }

                    val tables = syncTables.map { localTable ->
                        val remoteTable = resolveRemoteTableName(localTable, remoteNames)
                        mapTableDiagnostic(sqliteDb, dbConn, resolvedDatabase, localTable, remoteTable)
                    }

                    if (tables.none { it.remoteTable != null }) {
                        notes.add("Aucune table mappable detectee dans la base distante.")
                    }

                    OnlineDiagnosticReport(
                        host = config.host,
                        port = config.port,
                        configuredDatabase = config.forcedDatabase,
                        resolvedDatabase = resolvedDatabase,
                        tables = tables,
                        notes = notes
                    )
                }
            }
        }
    }

    private fun hasAllTargetTables(connection: Connection, dbName: String): Boolean {
        val names = remoteTableNames(connection, dbName)
        return coreTables.all { core -> resolveRemoteTableName(core, names) != null }
    }

    private fun resolveDatabaseName(context: Context, config: MariaDbConfig, connection: Connection): String {
        if (!config.forcedDatabase.isNullOrBlank()) {
            return config.forcedDatabase
        }

        val preferred = SettingsStore.getExternalDatabaseName(context)
        if (!preferred.isNullOrBlank() && hasAllTargetTables(connection, preferred)) {
            return preferred
        }

        val candidates = mutableListOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SHOW DATABASES").use { rs ->
                while (rs.next()) {
                    val db = rs.getString(1)
                    if (!db.equals("information_schema", true) &&
                        !db.equals("mysql", true) &&
                        !db.equals("performance_schema", true) &&
                        !db.equals("sys", true)
                    ) {
                        candidates.add(db)
                    }
                }
            }
        }

        return candidates.firstOrNull { hasAllTargetTables(connection, it) }
            ?: throw SQLException("Aucune base distante compatible trouvee")
    }

    private fun sqliteTableColumns(sqliteDb: SQLiteDatabase, table: String): List<String> {
        val cols = mutableListOf<String>()
        sqliteDb.rawQuery("PRAGMA table_info(${escapeIdent(table)})", null).use { c ->
            if (c.moveToFirst()) {
                do {
                    cols.add(c.getString(1))
                } while (c.moveToNext())
            }
        }
        return cols
    }

    private fun getLocalPrimaryKey(sqliteDb: SQLiteDatabase, table: String): String? {
        sqliteDb.rawQuery("PRAGMA table_info(${escapeIdent(table)})", null).use { c ->
            val numIdx = c.getColumnIndex("pk")
            val nameIdx = c.getColumnIndex("name")
            if (numIdx >= 0 && nameIdx >= 0 && c.moveToFirst()) {
                do {
                    if (c.getInt(numIdx) > 0) return c.getString(nameIdx)
                } while (c.moveToNext())
            }
        }
        return null
    }

    private fun resultSetValueToContent(values: ContentValues, column: String, raw: Any?) {
        when (raw) {
            null -> values.putNull(column)
            is Int -> values.put(column, raw)
            is Long -> values.put(column, raw)
            is Short -> values.put(column, raw.toInt())
            is Float -> values.put(column, raw)
            is Double -> values.put(column, raw)
            is ByteArray -> values.put(column, raw)
            is Boolean -> values.put(column, if (raw) 1 else 0)
            else -> values.put(column, raw.toString())
        }
    }

    private fun pullTableFromRemote(
        sqliteDb: SQLiteDatabase,
        remoteConn: Connection,
        databaseName: String,
        localTable: String,
        remoteTable: String
    ): Boolean {
        val columns = sqliteTableColumns(sqliteDb, localTable)
        if (columns.isEmpty()) return false
        val remoteColumns = remoteTableColumns(remoteConn, databaseName, remoteTable)

        val mappedColumns = columns.mapNotNull { localColumn ->
            resolveRemoteColumnName(localColumn, remoteColumns)?.let { remoteColumn ->
                localColumn to remoteColumn
            }
        }
        if (mappedColumns.isEmpty()) return false

        val sqlColumns = mappedColumns.joinToString(", ") { (_, remoteColumn) -> escapeIdent(remoteColumn) }
        val query = "SELECT $sqlColumns FROM ${escapeIdent(databaseName)}.${escapeIdent(remoteTable)}"
        val localPk = getLocalPrimaryKey(sqliteDb, localTable)

        sqliteDb.beginTransaction()
        try {
            sqliteDb.delete(localTable, null, null)
            sqliteDb.delete("_sync_history", "table_name = ?", arrayOf(localTable))

            remoteConn.createStatement().use { st ->
                st.executeQuery(query).use { rs ->
                    while (rs.next()) {
                        val values = ContentValues()
                        mappedColumns.forEachIndexed { index, (localColumn, _) ->
                            resultSetValueToContent(values, localColumn, rs.getObject(index + 1))
                        }
                        sqliteDb.insert(localTable, null, values)
                        
                        if (localPk != null) {
                            val pkVal = values.getAsString(localPk)
                            if (pkVal != null) {
                                val h = ContentValues().apply {
                                    put("table_name", localTable)
                                    put("pk_val", pkVal)
                                }
                                sqliteDb.insert("_sync_history", null, h)
                            }
                        }
                    }
                }
            }

            sqliteDb.setTransactionSuccessful()
        } finally {
            sqliteDb.endTransaction()
        }
        return true
    }

    private fun bindCursorValue(psIndex: Int, cursor: Cursor, colIndex: Int, ps: java.sql.PreparedStatement) {
        when (cursor.getType(colIndex)) {
            Cursor.FIELD_TYPE_NULL -> ps.setObject(psIndex, null)
            Cursor.FIELD_TYPE_INTEGER -> ps.setLong(psIndex, cursor.getLong(colIndex))
            Cursor.FIELD_TYPE_FLOAT -> ps.setDouble(psIndex, cursor.getDouble(colIndex))
            Cursor.FIELD_TYPE_BLOB -> ps.setBytes(psIndex, cursor.getBlob(colIndex))
            else -> ps.setString(psIndex, cursor.getString(colIndex))
        }
    }

    private fun pushTableToRemote(
        sqliteDb: SQLiteDatabase,
        remoteConn: Connection,
        databaseName: String,
        localTable: String,
        remoteTable: String
    ): Boolean {
        val localColumns = sqliteTableColumns(sqliteDb, localTable)
        if (localColumns.isEmpty()) return false
        val remoteColumns = remoteTableColumns(remoteConn, databaseName, remoteTable)

        val mappedColumns = remoteColumns.mapNotNull { remoteColumn ->
            resolveLocalColumnName(remoteColumn, localColumns)?.let { localColumn ->
                localColumn to remoteColumn
            }
        }
        if (mappedColumns.isEmpty()) {
            if (localTableHasData(sqliteDb, localTable)) {
                throw SQLException(
                    "Aucune colonne compatible pour pousser '$localTable' vers '$remoteTable'"
                )
            }
            return false
        }

        val localPk = getLocalPrimaryKey(sqliteDb, localTable)
        val remotePkMapping = if (localPk != null) mappedColumns.find { it.first == localPk } else null

        if (localPk != null && remotePkMapping != null) {
            val remotePk = remotePkMapping.second
            val localIds = mutableSetOf<String>()
            sqliteDb.rawQuery("SELECT ${escapeIdent(localPk)} FROM ${escapeIdent(localTable)}", null).use { c ->
                if (c.moveToFirst()) {
                    do {
                        localIds.add(c.getString(0))
                    } while (c.moveToNext())
                }
            }

            val historyIds = mutableSetOf<String>()
            sqliteDb.rawQuery("SELECT pk_val FROM _sync_history WHERE table_name = ?", arrayOf(localTable)).use { c ->
                if (c.moveToFirst()) {
                    do {
                        historyIds.add(c.getString(0))
                    } while (c.moveToNext())
                }
            }

            val deletedIds = historyIds - localIds
            if (deletedIds.isNotEmpty()) {
                val inClause = deletedIds.joinToString(",") { "?" }
                val delSql = "DELETE FROM ${escapeIdent(databaseName)}.${escapeIdent(remoteTable)} WHERE ${escapeIdent(remotePk)} IN ($inClause)"
                remoteConn.prepareStatement(delSql).use { ps ->
                    var idx = 1
                    deletedIds.forEach { id ->
                        ps.setString(idx++, id)
                    }
                    ps.executeUpdate()
                }
            }
            
            sqliteDb.delete("_sync_history", "table_name = ?", arrayOf(localTable))
            sqliteDb.beginTransaction()
            try {
                localIds.forEach { id ->
                    val h = ContentValues().apply {
                        put("table_name", localTable)
                        put("pk_val", id)
                    }
                    sqliteDb.insert("_sync_history", null, h)
                }
                sqliteDb.setTransactionSuccessful()
            } finally {
                sqliteDb.endTransaction()
            }
        }

        val escapedColumns = mappedColumns.joinToString(", ") { (_, remoteColumn) -> escapeIdent(remoteColumn) }
        val placeholders = mappedColumns.joinToString(", ") { "?" }
        val selectColumns = mappedColumns.joinToString(", ") { (localColumn, _) -> escapeIdent(localColumn) }
        
        val updateClause = mappedColumns.joinToString(", ") { (_, remoteColumn) -> 
            "${escapeIdent(remoteColumn)} = VALUES(${escapeIdent(remoteColumn)})"
        }
        
        val insertSql = "INSERT INTO ${escapeIdent(databaseName)}.${escapeIdent(remoteTable)} ($escapedColumns) VALUES ($placeholders) ON DUPLICATE KEY UPDATE $updateClause"
        val selectSql = "SELECT $selectColumns FROM ${escapeIdent(localTable)}"

        try {
            sqliteDb.rawQuery(selectSql, null).use { cursor ->
                remoteConn.prepareStatement(insertSql).use { ps ->
                    if (cursor.moveToFirst()) {
                        do {
                            mappedColumns.indices.forEach { i ->
                                bindCursorValue(i + 1, cursor, i, ps)
                            }
                            ps.addBatch()
                        } while (cursor.moveToNext())
                    }
                    ps.executeBatch()
                }
            }
        } catch (e: SQLException) {
            throw SQLException(
                "Echec insertion table distante '$remoteTable' depuis '$localTable' : ${e.message}",
                e.sqlState,
                e.errorCode,
                e
            )
        }
        return true
    }

    fun connectAndPull(context: Context): Result<String> {
        return runCatching {
            val config = resolveConfig(context)
            openServerConnection(config).use { serverConn ->
                val dbName = resolveDatabaseName(context, config, serverConn)
                openDatabaseConnection(config, dbName).use { dbConn ->
                    val sqliteDb = DatabaseHelper(context).getDatabaseForMode(useExternal = true)
                    FutureRecettesManager.ensureSchema(sqliteDb)
                    val remoteNames = remoteTableNames(dbConn, dbName)
                    var synced = 0
                    syncTables.forEach { localTable ->
                        val remoteTable = resolveRemoteTableName(localTable, remoteNames)
                        if (remoteTable != null) {
                            if (pullTableFromRemote(sqliteDb, dbConn, dbName, localTable, remoteTable)) {
                                synced++
                            }
                        }
                    }
                    if (synced == 0) {
                        throw SQLException("Aucune table utilisable trouvee dans la base distante '$dbName'")
                    }
                }
                SettingsStore.setExternalDatabaseName(context, dbName)
                SettingsStore.setExternalDataSourceEnabled(context, true)
                DatabaseHelper.closeActiveDatabase()
                dbName
            }
        }
    }

    fun pushExternalToRemote(context: Context): Result<Int> {
        return runCatching {
            val config = resolveConfig(context)
            val dbName = SettingsStore.getExternalDatabaseName(context)
                ?: throw SQLException("Nom de base distante introuvable")

            openDatabaseConnection(config, dbName).use { dbConn ->
                dbConn.autoCommit = false
                try {
                    val sqliteDb = DatabaseHelper(context).getDatabaseForMode(useExternal = true)
                    FutureRecettesManager.ensureSchema(sqliteDb)
                    val remoteNames = remoteTableNames(dbConn, dbName)

                    val unresolvedWithData = syncTables.filter { localTable ->
                        resolveRemoteTableName(localTable, remoteNames) == null && localTableHasData(sqliteDb, localTable)
                    }
                    val unresolvedRequiredWithData = unresolvedWithData.filter { it in requiredRemoteTables }
                    if (unresolvedRequiredWithData.isNotEmpty()) {
                        throw SQLException(
                            "Tables distantes requises manquantes pour donnees locales: ${unresolvedRequiredWithData.joinToString(", ")}"
                        )
                    }

                    val resolvedTables = syncTables.mapNotNull { localTable ->
                        resolveRemoteTableName(localTable, remoteNames)?.let { remoteTable ->
                            ResolvedTable(localTable = localTable, remoteTable = remoteTable)
                        }
                    }
                    if (resolvedTables.isEmpty()) {
                        throw SQLException("Aucune table distante mappable trouvee pour '$dbName'")
                    }

                    // Delete children first to satisfy foreign keys, then insert parents first.
                    // resolvedTables.asReversed().forEach { table ->
                    //     clearRemoteTable(dbConn, dbName, table.remoteTable)
                    // }

                    var pushed = 0
                    resolvedTables.forEach { table ->
                        if (pushTableToRemote(sqliteDb, dbConn, dbName, table.localTable, table.remoteTable)) {
                            pushed++
                        }
                    }
                    if (pushed == 0) {
                        throw SQLException("Aucune table n'a pu etre synchronisee vers la base distante '$dbName'")
                    }
                    dbConn.commit()
                    pushed
                } catch (e: Exception) {
                    dbConn.rollback()
                    throw e
                } finally {
                    dbConn.autoCommit = true
                }
            }
        }
    }
}













