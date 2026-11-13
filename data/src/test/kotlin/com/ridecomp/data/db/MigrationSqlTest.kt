package com.ridecomp.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ridecomp.core.model.BuiltInProfiles
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
    fun `migration 2 to 3 creates the exported profiles table and seeds the built-ins`() {
        val executed = executedSql { RideCompMigrations.MIGRATION_2_3.migrate(it) }
        val (creates, inserts) = executed.partition { it.startsWith(CREATE) }

        assertEquals(exportedCreateSql(3, DbNames.TABLE_ACTIVITY_PROFILES), creates)
        assertEquals(BuiltInProfiles.ALL.size, inserts.size)
    }

    @Test
    fun `migration 3 to 4 matches the exported contacts and incidents schema`() {
        val executed = executedSql { RideCompMigrations.MIGRATION_3_4.migrate(it) }

        assertEquals(
            exportedCreateSql(4, DbNames.TABLE_EMERGENCY_CONTACTS) + exportedCreateSql(4, DbNames.TABLE_INCIDENTS),
            executed,
        )
    }

    @Test
    fun `every schema version has a migration path`() {
        val versions = File(SCHEMA_DIR).listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }

        assertEquals(RideCompDatabase.VERSION, versions.max())
        assertTrue(RideCompMigrations.ALL.size == RideCompDatabase.VERSION - 1)
    }

    private companion object {
        const val CREATE = "CREATE"
        const val SCHEMA_DIR = "schemas/com.ridecomp.data.db.RideCompDatabase"
    }
}
