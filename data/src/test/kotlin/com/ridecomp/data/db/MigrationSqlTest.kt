package com.ridecomp.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM check that each migration creates exactly what Room exported for the new version (DATA-02), so a
 * hand-written SQL typo fails the build even without a device. The full migration test with data runs on
 * a device (androidTest/RideCompMigrationTest).
 */
class MigrationSqlTest {
    private fun executedSql(run: (SupportSQLiteDatabase) -> Unit): List<String> {
        val statements = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args[0] as String
            null
        } as SupportSQLiteDatabase
        run(db)
        return statements
    }

    private fun exportedCreateSql(version: Int, table: String): List<String> {
        val json = File(SCHEMA_DIR, "$version.json").readText()
        val tableBlock = json.substringAfter("\"tableName\": \"$table\"").substringBefore("\"tableName\"")
        return Regex("\"createSql\": \"(.*?)\"").findAll(tableBlock)
            .map { it.groupValues[1].replace("\${TABLE_NAME}", table).replace("\\\"", "\"") }
            .toList()
    }

    @Test
    fun `migration 1 to 2 matches the exported sensors schema`() {
        val executed = executedSql { RideCompMigrations.MIGRATION_1_2.migrate(it) }

        assertEquals(exportedCreateSql(2, DbNames.TABLE_SENSORS), executed)
    }

    @Test
    fun `every schema version has a migration path`() {
        val versions = File(SCHEMA_DIR).listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }

        assertEquals(RideCompDatabase.VERSION, versions.max())
        assertTrue(RideCompMigrations.ALL.size == RideCompDatabase.VERSION - 1)
    }

    private companion object {
        const val SCHEMA_DIR = "schemas/com.ridecomp.data.db.RideCompDatabase"
    }
}
