package id.schoolmate.app.data.local

import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Reads the exported Room schemas (app/schemas) that are committed to the repository. */
object RoomSchemaFiles {
    private val dir: File by lazy {
        val path = System.getProperty("schoolmate.roomSchemaDir")
            ?: error("System property schoolmate.roomSchemaDir is not set (configured in app/build.gradle.kts)")
        File(path, SchoolMateDatabase::class.java.name).also { require(it.isDirectory) { "Missing schema dir $it" } }
    }

    fun database(version: Int): JsonObject =
        Json.parseToJsonElement(File(dir, "$version.json").readText()).jsonObject["database"]!!.jsonObject

    /** Every CREATE statement (tables + indices) of [version], as Room would run it. */
    fun createStatements(version: Int): List<String> {
        val db = database(version)
        return db["entities"]!!.jsonArray.flatMap { e ->
            val entity = e.jsonObject
            val table = entity["tableName"]!!.jsonPrimitive.content
            val create = entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            val indices = entity["indices"]?.jsonArray.orEmpty().map {
                it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }
            listOf(create) + indices
        } + db["setupQueries"]!!.jsonArray.map { it.jsonPrimitive.content }
    }

    /** Creates a database file exactly as app version with schema [version] would have left it. */
    fun createDatabaseAt(file: File, version: Int): SQLiteDatabase {
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        createStatements(version).forEach(db::execSQL)
        db.version = version
        return db
    }

    /** Table name -> normalized CREATE TABLE sql for [version]. */
    fun tableSql(version: Int): Map<String, String> =
        database(version)["entities"]!!.jsonArray.associate { e ->
            val table = e.jsonObject["tableName"]!!.jsonPrimitive.content
            table to normalize(e.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
        }

    fun normalize(sql: String): String =
        sql.replace("IF NOT EXISTS ", "").replace(Regex("\\s+"), " ").replace(" )", ")").replace(" ,", ",").trim()
}
