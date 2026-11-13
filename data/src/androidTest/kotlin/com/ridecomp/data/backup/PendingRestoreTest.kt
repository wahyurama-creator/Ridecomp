package com.ridecomp.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.RideCompDatabase
import com.ridecomp.data.db.entity.RouteEntity
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device test (D-39, STB-01): a restore staged by the previous run replaces the data before the first
 * query, now that it is applied lazily by the open helper instead of in `RideCompDatabase.build`.
 */
@RunWith(AndroidJUnit4::class)
class PendingRestoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Main
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }

    private val logger = object : Logger {
        override fun debug(tag: String, message: () -> String) = Unit

        override fun info(tag: String, message: () -> String) = Unit

        override fun warn(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun error(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private fun route(name: String) =
        RouteEntity(name = name, sourceFile = null, distanceM = 1.0, ascentM = 0.0, createdAt = 0)

    @Test
    fun staged_restore_is_applied_before_the_first_query() = runTest {
        context.deleteDatabase(DbNames.DATABASE_FILE)
        val candidate = File(context.cacheDir, CANDIDATE_NAME).apply { delete() }
        val before = RideCompDatabase.build(context)
        before.routeDao().insertRoute(route(KEPT))
        RoomBackupDatabase(context, before, dispatchers, logger).snapshot(candidate)
        before.routeDao().insertRoute(route(DROPPED))
        before.close()

        PendingRestore.stage(context, candidate)
        val after = RideCompDatabase.build(context)

        assertEquals(listOf(KEPT), after.routeDao().observeAll().first().map { it.name })
        val live = context.getDatabasePath(DbNames.DATABASE_FILE)
        assertFalse(File(live.path + PENDING_SUFFIX).exists())
        after.close()
        candidate.delete()
        context.deleteDatabase(DbNames.DATABASE_FILE)
    }

    private companion object {
        const val CANDIDATE_NAME = "restore-candidate.db"
        const val KEPT = "kept"
        const val DROPPED = "dropped"
        const val PENDING_SUFFIX = ".pending"
    }
}
