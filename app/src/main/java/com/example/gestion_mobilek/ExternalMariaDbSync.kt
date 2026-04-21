package com.example.gestion_mobilek

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
    private val coreTables = setOf("personnes", "gouts", "plats")

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

    private fun resolveConfig(): MariaDbConfig {
        val host = BuildConfig.MARIADB_HOST.trim()
        val port = BuildConfig.MARIADB_PORT
        val user = BuildConfig.MARIADB_USER.trim()
        val password = BuildConfig.MARIADB_PASSWORD
        val forcedDatabase = BuildConfig.MARIADB_DATABASE.trim().ifBlank { null }

        if (host.isBlank() || port <= 0 || user.isBlank() || password.isBlank()) {
            throw SQLException("Configuration MariaDB manquante: ajoutez le fichier .env")
        }

        return MariaDbConfig(
            host = host,
            port = port,
            user = user,
            password = password,
            forcedDatabase = forcedDatabase
        )
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
        return remoteNames.firstOrNull { normalizeTableName(it).equals(normalizedLocal, ignoreCase = true) }
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
        if (remoteColumns.contains(localColumn)) return localColumn
        val normalized = normalizeTableName(localColumn)
        return remoteColumns.firstOrNull { normalizeTableName(it).equals(normalized, ignoreCase = true) }
    }

    private fun resolveLocalColumnName(remoteColumn: String, localColumns: List<String>): String? {
        if (localColumns.contains(remoteColumn)) return remoteColumn
        val normalized = normalizeTableName(remoteColumn)
        return localColumns.firstOrNull { normalizeTableName(it).equals(normalized, ignoreCase = true) }
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

        sqliteDb.beginTransaction()
        try {
            sqliteDb.delete(localTable, null, null)

            remoteConn.createStatement().use { st ->
                st.executeQuery(query).use { rs ->
                    while (rs.next()) {
                        val values = ContentValues()
                        mappedColumns.forEachIndexed { index, (localColumn, _) ->
                            resultSetValueToContent(values, localColumn, rs.getObject(index + 1))
                        }
                        sqliteDb.insert(localTable, null, values)
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
        if (mappedColumns.isEmpty()) return false

        val escapedColumns = mappedColumns.joinToString(", ") { (_, remoteColumn) -> escapeIdent(remoteColumn) }
        val placeholders = mappedColumns.joinToString(", ") { "?" }
        val selectColumns = mappedColumns.joinToString(", ") { (localColumn, _) -> escapeIdent(localColumn) }
        val deleteSql = "DELETE FROM ${escapeIdent(databaseName)}.${escapeIdent(remoteTable)}"
        val insertSql = "INSERT INTO ${escapeIdent(databaseName)}.${escapeIdent(remoteTable)} ($escapedColumns) VALUES ($placeholders)"
        val selectSql = "SELECT $selectColumns FROM ${escapeIdent(localTable)}"

        remoteConn.createStatement().use { it.executeUpdate(deleteSql) }

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
        return true
    }

    fun connectAndPull(context: Context): Result<String> {
        return runCatching {
            val config = resolveConfig()
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

    fun pushExternalToRemote(context: Context): Result<String> {
        return runCatching {
            val config = resolveConfig()
            val dbName = SettingsStore.getExternalDatabaseName(context)
                ?: throw SQLException("Nom de base distante introuvable")

            openDatabaseConnection(config, dbName).use { dbConn ->
                dbConn.autoCommit = false
                try {
                    val sqliteDb = DatabaseHelper(context).getDatabaseForMode(useExternal = true)
                    FutureRecettesManager.ensureSchema(sqliteDb)
                    val remoteNames = remoteTableNames(dbConn, dbName)
                    syncTables.forEach { localTable ->
                        val remoteTable = resolveRemoteTableName(localTable, remoteNames)
                        if (remoteTable != null) {
                            pushTableToRemote(sqliteDb, dbConn, dbName, localTable, remoteTable)
                        }
                    }
                    dbConn.commit()
                } catch (e: Exception) {
                    dbConn.rollback()
                    throw e
                } finally {
                    dbConn.autoCommit = true
                }
            }
            dbName
        }
    }
}






