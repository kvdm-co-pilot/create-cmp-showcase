package com.kvdm.fuelled.inspector

import com.kvdm.fuelled.data.local.AppDatabase
import androidx.room.useReaderConnection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.context.GlobalContext

/**
 * `GET /inspect/db` (schema: tables via `sqlite_master`) and `GET /inspect/db?table=<name>&limit=<n>`
 * (rows). Read-only, off the main thread. 404 when this project's local-database feature is off
 * (there is nothing to query) — see the `room`-gated implementation below for the real path.
 */
object DbInspector {

    // Reads go through the project's Room database (a Koin single). Injection-safe by
    // construction: a requested `table` is only ever used in a query after it is proven to be
    // a real name returned by `sqlite_master` in THIS call — the raw wire value never reaches
    // SQL beyond that validated identifier.
    //
    // This project's Room config uses the KMP driver architecture (BundledSQLiteDriver, see
    // data/local/DatabaseBuilder.kt), not the legacy Android-only SupportSQLiteDatabase — so
    // reads go through Room 2.8's public `useReaderConnection { transactor -> ... }` whose
    // receiver is a pooled connection exposing `usePrepared(sql) { stmt -> ... }` (the
    // statement is created and closed by Room; binds are 1-based, column reads 0-based).
    private const val DEFAULT_ROW_LIMIT = 50
    private const val MAX_ROW_LIMIT = 500
    private val VALID_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val prettyJson = Json { prettyPrint = true }

    private fun appDatabaseOrNull(): AppDatabase? =
        try {
            // GlobalContext.getOrNull() IS the Koin instance (or null before startKoin).
            GlobalContext.getOrNull()?.getOrNull<AppDatabase>()
        } catch (t: Throwable) {
            null
        }

    fun schema(): Pair<Int, String> {
        val db = appDatabaseOrNull()
            ?: return 503 to errorJson("database not available yet (Room not initialised — is Koin started?).")
        return try {
            val tables = runBlocking {
                db.useReaderConnection { connection ->
                    connection.usePrepared(
                        "SELECT name, sql FROM sqlite_master WHERE type = 'table' " +
                            "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' ORDER BY name"
                    ) { stmt ->
                        val out = mutableListOf<Pair<String, String?>>()
                        while (stmt.step()) {
                            out += stmt.getText(0) to (if (stmt.isNull(1)) null else stmt.getText(1))
                        }
                        out
                    }
                }
            }
            200 to prettyJson.encodeToString(JsonElement.serializer(), buildJsonObject {
                put("tables", buildJsonArray {
                    tables.forEach { (name, sql) ->
                        add(buildJsonObject {
                            put("name", JsonPrimitive(name))
                            put("sql", sql?.let { JsonPrimitive(it) } ?: JsonNull)
                        })
                    }
                })
            })
        } catch (t: Throwable) {
            500 to errorJson("failed to read schema: ${t.message}")
        }
    }

    /** Row page for one table: columns + stringified values, capped. */
    private class TableRows(val columns: List<String>, val rows: List<JsonElement>)

    fun rows(table: String, limitParam: String?): Pair<Int, String> {
        if (!VALID_IDENTIFIER.matches(table)) {
            return 400 to errorJson("invalid table name '$table' — expected a plain SQL identifier.")
        }
        val db = appDatabaseOrNull()
            ?: return 503 to errorJson("database not available yet (Room not initialised — is Koin started?).")
        val limit = (limitParam?.toIntOrNull() ?: DEFAULT_ROW_LIMIT).coerceIn(1, MAX_ROW_LIMIT)
        return try {
            val result: TableRows? = runBlocking {
                db.useReaderConnection { connection ->
                    // STRICT validation: `table` is only used in the row query below once THIS
                    // check proves it is a real sqlite_master identifier — never the raw wire value.
                    val exists = connection.usePrepared(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?"
                    ) { stmt ->
                        stmt.bindText(1, table)
                        stmt.step()
                    }
                    if (!exists) return@useReaderConnection null

                    connection.usePrepared("SELECT * FROM \"$table\" LIMIT ?") { stmt ->
                        stmt.bindLong(1, limit.toLong())
                        val colCount = stmt.getColumnCount()
                        val columns = (0 until colCount).map { stmt.getColumnName(it) }
                        val rows = mutableListOf<JsonElement>()
                        while (stmt.step()) {
                            rows += buildJsonObject {
                                for (i in 0 until colCount) {
                                    put(columns[i], if (stmt.isNull(i)) JsonNull else JsonPrimitive(stmt.getText(i)))
                                }
                            }
                        }
                        TableRows(columns, rows)
                    }
                }
            }
            if (result == null) {
                return 404 to errorJson("unknown table '$table' — not present in sqlite_master.")
            }
            200 to prettyJson.encodeToString(JsonElement.serializer(), buildJsonObject {
                put("table", JsonPrimitive(table))
                put("columns", buildJsonArray { result.columns.forEach { add(JsonPrimitive(it)) } })
                put("rows", buildJsonArray { result.rows.forEach { add(it) } })
                put("rowCount", JsonPrimitive(result.rows.size))
            })
        } catch (t: Throwable) {
            500 to errorJson("failed to read table '$table': ${t.message}")
        }
    }

    private fun errorJson(message: String): String =
        """{"error":${JsonPrimitive(message)}}"""
}
