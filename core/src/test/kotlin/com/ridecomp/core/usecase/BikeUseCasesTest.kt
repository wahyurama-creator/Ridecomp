package com.ridecomp.core.usecase

import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.withBike
import com.ridecomp.core.testing.FakeBikeRepository
import com.ridecomp.core.util.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class BikeUseCasesTest {
    private val repository = FakeBikeRepository()

    @Test
    fun `the last used bike is preselected and falls back to the first`() = runTest {
        val choice = ObserveBikeChoiceUseCase(repository)
        repository.save(Bike(name = "MTB", profileId = 3))

        assertEquals(1L, choice().first().selected?.id)

        SelectBikeUseCase(repository)(2)
        assertEquals(2L, choice().first().selected?.id)
        assertEquals(2L, GetSelectedBikeUseCase(repository)()?.id)

        repository.delete(2)
        assertEquals(1L, choice().first().selected?.id)
    }

    @Test
    fun `a new bike is saved trimmed and becomes the selected one`() = runTest {
        val result = SaveBikeUseCase(repository)(Bike(name = "  Polygon Xtrada  ", profileId = 3, weightKg = 13.5))

        val id = assertIs<Outcome.Success<Long>>(result).value
        assertEquals("Polygon Xtrada", repository.get(id)?.name)
        assertEquals(id, GetSelectedBikeUseCase(repository)()?.id)
    }

    @Test
    fun `editing a bike keeps the selection`() = runTest {
        val save = SaveBikeUseCase(repository)
        val mtb = (save(Bike(name = "MTB", profileId = 3)) as Outcome.Success).value
        SelectBikeUseCase(repository)(1)

        save(Bike(id = mtb, name = "MTB", profileId = 3, weightKg = 14.0))

        assertEquals(1L, GetSelectedBikeUseCase(repository)()?.id)
        assertEquals(14.0, repository.get(mtb)?.weightKg)
    }

    @Test
    fun `weights and wheels out of range are refused with every bad field`() = runTest {
        val result = SaveBikeUseCase(repository)(Bike(profileId = 1, weightKg = Double.NaN, wheelCircumferenceMm = 500))

        assertEquals(
            setOf(BikeField.WEIGHT, BikeField.WHEEL_CIRCUMFERENCE),
            assertIs<Outcome.Failure<InvalidBike>>(result).error.fields,
        )
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun `the last bike cannot be deleted`() = runTest {
        val delete = DeleteBikeUseCase(repository)

        assertFalse(delete(1))
        repository.save(Bike(name = "Gravel", profileId = 2))
        assertFalse(delete(99))
        assertTrue(delete(1))
        assertEquals(listOf(2L), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `the rider takes the bike's weight and wheel size`() {
        val rider = RiderProfile(weightKg = 68.0, bikeWeightKg = 9.0, wheelCircumferenceMm = 2_105)

        val onBike = rider.withBike(Bike(profileId = 3, weightKg = 13.5, wheelCircumferenceMm = 2_273))

        assertEquals(RiderProfile(weightKg = 68.0, bikeWeightKg = 13.5, wheelCircumferenceMm = 2_273), onBike)
    }
}
