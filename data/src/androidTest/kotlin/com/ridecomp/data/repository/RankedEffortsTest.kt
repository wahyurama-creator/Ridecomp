package com.ridecomp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.engine.map.MapBounds
import com.ridecomp.core.engine.segments.Segment
import com.ridecomp.core.engine.segments.SegmentMatch
import com.ridecomp.core.engine.segments.SegmentPoint
import com.ridecomp.core.engine.segments.SegmentSource
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.repository.SegmentEffort
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

/** Ranks of the "Results" card (T11.7) on real SQLite: earlier rides only, a tie is not a record. */
@RunWith(AndroidJUnit4::class)
class RankedEffortsTest {
    private lateinit var db: RideCompDatabase
    private lateinit var efforts: BestEffortRepositoryImpl
    private lateinit var segments: SegmentRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RideCompDatabase::class.java)
            .build()
        efforts = BestEffortRepositoryImpl(db.bestEffortDao())
        segments = SegmentRepositoryImpl(db.segmentDao())
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
    fun effort_ranks_count_earlier_rides_of_the_kind_as_good_or_better() = runTest {
        val first = ride(startedAt = 1_000)
        val second = ride(startedAt = 2_000)
        val third = ride(startedAt = 3_000)
        val indoor = ride(startedAt = 4_000)
        efforts.replace(first, ActivityKind.ROAD, listOf(effort(KM, 120.0), effort(POWER, 500.0)))
        efforts.replace(second, ActivityKind.ROAD, listOf(effort(KM, 100.0), effort(POWER, 450.0)))
        efforts.replace(third, ActivityKind.ROAD, listOf(effort(KM, 110.0), effort(POWER, 500.0)))
        efforts.replace(indoor, ActivityKind.INDOOR, listOf(effort(KM, 130.0)))

        val ranks = efforts.observeRanked(third).first().associate { it.effort.key to it.rank }

        assertEquals(mapOf(KM to 2, POWER to 2), ranks)
        assertEquals(1, efforts.observeRanked(second).first().first { it.effort.key == KM }.rank)
        assertEquals(1, efforts.observeRanked(indoor).first().single().rank)
    }

    @Test
    fun segment_passes_come_in_ride_order_with_their_rank() = runTest {
        val first = ride(startedAt = 1_000)
        val second = ride(startedAt = 2_000)
        val climb = segments.create(segment("Climb"))
        val sprint = segments.create(segment("Sprint"))
        segments.replaceForActivity(first, listOf(pass(climb, first, startMs = 0, seconds = 100.0)))
        segments.replaceForActivity(
            second,
            listOf(
                pass(sprint, second, startMs = 50, seconds = 30.0),
                pass(climb, second, startMs = 10, seconds = 120.0),
            ),
        )

        val passes = segments.observeRanked(second).first()

        assertEquals(listOf("Climb" to 2, "Sprint" to 1), passes.map { it.segmentName to it.rank })
        assertEquals(SEGMENT_M, passes.first().lengthM)
    }

    private fun segment(name: String) = Segment(
        id = 0,
        name = name,
        lengthM = SEGMENT_M,
        gainM = 0.0,
        avgGradePercent = 0.0,
        points = listOf(SegmentPoint(0.0, 0.0, 0.0), SegmentPoint(0.0, 0.0, SEGMENT_M)),
        bounds = MapBounds(0.0, 0.0, 0.0, 0.0),
        source = SegmentSource.ACTIVITY,
        createdAtMs = 0,
    )

    private fun pass(segmentId: Long, activityId: Long, startMs: Long, seconds: Double) = SegmentEffort(
        segmentId,
        activityId,
        SegmentMatch(startMs, startMs + seconds.toLong(), seconds, SEGMENT_M / seconds, null, null),
        isPr = false,
    )

    private companion object {
        val KM = BestEffortKey.FASTEST_1_KM
        val POWER = BestEffortKey.POWER_5_S
        const val SEGMENT_M = 1_000.0
    }
}
