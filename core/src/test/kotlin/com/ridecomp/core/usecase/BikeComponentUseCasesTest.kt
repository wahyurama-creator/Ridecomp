package com.ridecomp.core.usecase

import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.ComponentType
import com.ridecomp.core.testing.FakeBikeComponentRepository
import com.ridecomp.core.testing.FakeBikeRide
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.util.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class BikeComponentUseCasesTest {
    private val repository = FakeBikeComponentRepository()
    private val wear = ObserveComponentWearUseCase(repository)

    @Test
    fun `a part counts the rides on its bike since it was fitted, plus what it already had`() = runTest {
        repository.rides.value = listOf(
            FakeBikeRide(bikeId = 1, startedAtMs = 500, distanceM = 9_000.0),
            FakeBikeRide(bikeId = 1, startedAtMs = 2_000, distanceM = 40_000.0),
            FakeBikeRide(bikeId = 2, startedAtMs = 3_000, distanceM = 70_000.0),
        )
        SaveComponentUseCase(repository)(
            BikeComponent(bikeId = 1, type = ComponentType.CHAIN, installedAtMs = 1_000, initialDistanceM = 5_000.0),
        )

        val chain = wear(1).first().single()
        assertEquals(45_000.0, chain.distanceM)
        assertEquals(3_000_000.0, chain.component.limitM)
        assertFalse(chain.isDue)
        assertTrue(wear(2).first().isEmpty())
        assertEquals(49_000.0, ObserveBikeDistanceUseCase(repository)(1).first())
    }

    @Test
    fun `a part over its limit is due, a part without limit never is`() = runTest {
        val save = SaveComponentUseCase(repository)
        save(BikeComponent(bikeId = 1, type = ComponentType.BRAKE_PADS, installedAtMs = 0))
        save(BikeComponent(bikeId = 1, type = ComponentType.OTHER, installedAtMs = 0))
        repository.rides.value = listOf(FakeBikeRide(1, 10, 2_100_000.0))

        val due = wear.due().first()
        assertEquals(listOf(ComponentType.BRAKE_PADS), due.map { it.component.type })
        assertEquals(1f, due.single().fraction)
        assertNull(wear(1).first().last().fraction)
    }

    @Test
    fun `replacing restarts the count from now`() = runTest {
        val clock = FakeClock(5_000)
        val id = (
            SaveComponentUseCase(repository)(
                BikeComponent(bikeId = 1, type = ComponentType.TIRES, installedAtMs = 0, initialDistanceM = 800_000.0),
            ) as Outcome.Success
            ).value
        repository.rides.value = listOf(FakeBikeRide(1, 1_000, 3_500_000.0), FakeBikeRide(1, 6_000, 20_000.0))

        ReplaceComponentUseCase(repository, clock)(id)

        assertEquals(20_000.0, wear(1).first().single().distanceM)
    }

    @Test
    fun `limits and distances out of range are refused`() = runTest {
        val result = SaveComponentUseCase(repository)(
            BikeComponent(
                bikeId = 1,
                type = ComponentType.CHAIN,
                installedAtMs = 0,
                initialDistanceM = -1.0,
                limitM = 0.0,
            ),
        )

        assertEquals(
            setOf(ComponentField.LIMIT, ComponentField.INITIAL_DISTANCE),
            assertIs<Outcome.Failure<InvalidComponent>>(result).error.fields,
        )
        assertTrue(wear(1).first().isEmpty())
    }
}
