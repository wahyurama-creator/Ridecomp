package com.ridecomp.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridecomp.data.db.dao.PeriodTotalsRow
import com.ridecomp.data.db.entity.ActivityEntity
import com.ridecomp.data.db.entity.EventEntity
import com.ridecomp.data.db.entity.LapEntity
import com.ridecomp.data.db.entity.ProfileEntity
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.db.entity.TrackPointEntity
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RideCompDatabaseTest {
    private lateinit var db: RideCompDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RideCompDatabase::class.java)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun activity(status: String, startedAt: Long) = ActivityEntity(
        uuid = "uuid-$startedAt",
        name = "Ride $startedAt",
        status = status,
        startedAt = startedAt,
        lastCheckpointAt = startedAt,
    )

    private fun point(activityId: Long, t: Long) = TrackPointEntity(
        activityId = activityId,
        timeMs = t,
        lat = -7.0,
        lon = 110.0,
        altM = 100.0,
        gpsAccuracyM = 5.0,
        distanceM = t.toDouble(),
        speedMps = 8.0,
        hr = null,
        cadence = null,
        power = null,
        estPower = null,
        tempC = null,
    )

    @Test
    fun unfinished_rides_are_found_for_recovery() = runTest {
        val dao = db.activityDao()
        dao.insert(activity(DbNames.ActivityStatus.FINISHED, 1))
        dao.insert(activity(DbNames.ActivityStatus.RECORDING, 2))
        dao.insert(activity(DbNames.ActivityStatus.PAUSED, 3))

        val unfinished = dao.getUnfinished().map { it.status }

        assertEquals(listOf(DbNames.ActivityStatus.PAUSED, DbNames.ActivityStatus.RECORDING), unfinished)
    }

    @Test
    fun finished_rides_are_paged_newest_first() = runTest {
        val dao = db.activityDao()
        (1L..5L).forEach { dao.insert(activity(DbNames.ActivityStatus.FINISHED, it)) }

        val page = dao.observeFinishedPage(limit = 2, offset = 1).first().map { it.startedAt }

        assertEquals(listOf(4L, 3L), page)
        assertEquals(5, dao.observeFinishedCount().first())
    }

    @Test
    fun totals_since_count_only_finished_rides_from_that_time() = runTest {
        val dao = db.activityDao()
        (1L..4L).forEach {
            dao.insert(activity(DbNames.ActivityStatus.FINISHED, it).copy(distanceM = 1_000.0, ascentM = 10.0))
        }
        dao.insert(activity(DbNames.ActivityStatus.RECORDING, 5).copy(distanceM = 9_000.0))

        val totals = dao.observeTotalsSince(sinceMs = 3).first()

        assertEquals(PeriodTotalsRow(rides = 2, distanceM = 2_000.0, ascentM = 20.0), totals)
        assertEquals(0, dao.observeTotalsSince(sinceMs = 100).first().rides)
    }

    @Test
    fun track_points_are_read_in_chunks_and_reflush_is_idempotent() = runTest {
        val id = db.activityDao().insert(activity(DbNames.ActivityStatus.RECORDING, 1))
        val pointDao = db.trackPointDao()
        pointDao.insertAll((1L..10L).map { point(id, it) })
        pointDao.insertAll((6L..10L).map { point(id, it) })

        val chunk = pointDao.getChunk(id, afterTimeMs = 3, limit = 4).map { it.timeMs }

        assertEquals(10, pointDao.count(id))
        assertEquals(listOf(4L, 5L, 6L, 7L), chunk)
        assertEquals(10L, pointDao.getLast(id)?.timeMs)
    }

    @Test
    fun deleting_a_ride_cascades_to_children() = runTest {
        val id = db.activityDao().insert(activity(DbNames.ActivityStatus.FINISHED, 1))
        db.trackPointDao().insertAll(listOf(point(id, 1)))
        db.eventDao().insert(EventEntity(activityId = id, timeMs = 1, type = "start"))
        db.lapDao().upsert(
            LapEntity(id, 0, 1, 2, 10.0, 1, 5.0, 6.0, null, null, null, null, null, 0.0, "manual"),
        )

        db.activityDao().deleteById(id)

        assertEquals(0, db.trackPointDao().count(id))
        assertEquals(emptyList(), db.eventDao().getForActivity(id))
        assertEquals(emptyList(), db.lapDao().getForActivity(id))
    }

    @Test
    fun profile_is_a_single_row() = runTest {
        val dao = db.profileDao()
        val profile = ProfileEntity(
            weightKg = 70.0,
            bikeWeightKg = 9.0,
            wheelCircumferenceMm = 2105,
            hrMax = null,
            hrRest = null,
            ftpW = null,
            birthYear = null,
            sex = null,
            ridingPosition = "hoods",
            surface = "road",
        )

        dao.upsert(profile)
        dao.upsert(profile.copy(weightKg = 72.0))

        assertEquals(72.0, dao.get()?.weightKg)
    }

    @Test
    fun settings_upsert_and_delete() = runTest {
        val dao = db.settingsDao()

        dao.upsert(SettingEntity("units", "metric"))
        dao.upsert(SettingEntity("units", "imperial"))
        assertEquals("imperial", dao.get("units"))

        dao.delete("units")
        assertNull(dao.get("units"))
    }
}
