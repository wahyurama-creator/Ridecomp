package com.ridecomp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.RideCompDatabase
import com.ridecomp.data.db.entity.ActivityEntity
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Record queries on real SQLite (T4.1): the better value depends on the key; ties keep the earliest ride. */
@RunWith(AndroidJUnit4::class)
class BestEffortRepositoryTest {
    private lateinit var db: RideCompDatabase
    private lateinit var repository: BestEffortRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RideCompDatabase::class.java)
            .build()
        repository = BestEffortRepositoryImpl(db.bestEffortDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun ride(startedAt: Long): Long = db.activityDao().insert(
        ActivityEntity(
            uuid = "u$startedAt",
            name = "Ride $startedAt",
            status = DbNames.ActivityStatus.FINISHED,
            startedAt = startedAt,
            lastCheckpointAt = startedAt,
        ),
    )

    private fun effort(key: BestEffortKey, value: Double) = RideEffort(BestEffort(key, value, null, null), isPr = false)

    @Test
    fun records_take_the_lowest_time_and_the_highest_power_per_kind() = runTest {
        val first = ride(startedAt = 1_000)
        val second = ride(startedAt = 2_000)
        val indoor = ride(startedAt = 3_000)
        repository.replace(
            first,
            ActivityKind.ROAD,
            listOf(effort(BestEffortKey.FASTEST_1_KM, 120.0), effort(BestEffortKey.POWER_5_S, 500.0)),
        )
        repository.replace(
            second,
            ActivityKind.ROAD,
            listOf(effort(BestEffortKey.FASTEST_1_KM, 100.0), effort(BestEffortKey.POWER_5_S, 450.0)),
        )
        repository.replace(indoor, ActivityKind.INDOOR, listOf(effort(BestEffortKey.FASTEST_1_KM, 60.0)))

        val records = repository.observeRecords(ActivityKind.ROAD).first().associateBy { it.key }

        assertEquals(100.0, records.getValue(BestEffortKey.FASTEST_1_KM).value)
        assertEquals(second, records.getValue(BestEffortKey.FASTEST_1_KM).activityId)
        assertEquals(500.0, records.getValue(BestEffortKey.POWER_5_S).value)
        assertEquals(first, records.getValue(BestEffortKey.POWER_5_S).activityId)
        assertEquals(listOf(ActivityKind.ROAD, ActivityKind.INDOOR), repository.observeKinds().first())
        assertEquals(
            mapOf(BestEffortKey.FASTEST_1_KM to 120.0, BestEffortKey.POWER_5_S to 500.0),
            repository.bestsBefore(ActivityKind.ROAD, beforeStartedAtMs = 2_000),
        )
    }

    @Test
    fun a_tie_keeps_the_earliest_ride_and_replace_is_idempotent() = runTest {
        val first = ride(startedAt = 1_000)
        val second = ride(startedAt = 2_000)
        repository.replace(second, ActivityKind.ROAD, listOf(effort(BestEffortKey.LONGEST_RIDE, 50_000.0)))
        repository.replace(first, ActivityKind.ROAD, listOf(effort(BestEffortKey.LONGEST_RIDE, 50_000.0)))
        repository.replace(first, ActivityKind.ROAD, listOf(effort(BestEffortKey.LONGEST_RIDE, 50_000.0)))

        val records = repository.observeRecords(ActivityKind.ROAD).first()

        assertEquals(1, records.size)
        assertEquals(first, records.single().activityId)
    }
}
