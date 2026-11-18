package com.ridecomp.core.engine.share

import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeShareCardFiles
import com.ridecomp.core.usecase.GetShareTargetsUseCase
import com.ridecomp.core.usecase.ShareCardImageUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Share card privacy and shape (T4.6, 12-FEATURE-SPECS §11). */
class ShareCardLineTest {
    /** A 3 km ride going north, one point every 10 m. */
    private val ride = (0..300).map {
        LinePoint(
            latDeg = -7.8 + it * 10.0 / 111_320.0,
            lonDeg = 110.37,
            distanceM =
            it * 10.0,
        )
    }

    @Test
    fun `hiding the ends cuts 500 m at both ends`() {
        val full = ShareCardLine.of(ride, isEndsHidden = false)
        val hidden = ShareCardLine.of(ride, isEndsHidden = true)

        assertEquals(301, full.size)
        assertEquals(201, hidden.size)
    }

    @Test
    fun `points are fitted into the unit square with the ride's proportions`() {
        val line = ShareCardLine.of(ride, isEndsHidden = false)

        assertTrue(line.all { it.x in 0f..1f && it.y in 0f..1f })
        // A north-south line: centred horizontally, from the bottom (start) to the top (end).
        assertTrue(line.all { it.x == 0.5f })
        assertEquals(1f, line.first().y, TOLERANCE)
        assertEquals(0f, line.last().y, TOLERANCE)
    }

    @Test
    fun `a ride shorter than the hidden ends has no line`() {
        assertTrue(ShareCardLine.of(ride.take(80), isEndsHidden = true).isEmpty())
        assertTrue(ShareCardLine.of(emptyList(), isEndsHidden = false).isEmpty())
    }

    @Test
    fun `the card is saved under an app name and shared`() = runTest {
        val files = FakeShareCardFiles()

        ShareCardImageUseCase(files, FakeClock(nowMs = 42))(byteArrayOf(1, 2, 3))

        assertEquals(listOf("RideComp_card_42.png"), files.shared)
        assertEquals(listOf(ShareTarget.OTHER), files.targets)
        assertEquals(3, files.saved.getValue("RideComp_card_42.png").size)
    }

    @Test
    fun `direct share buttons only for installed apps, the share sheet always`() {
        val files = FakeShareCardFiles(installed = setOf(ShareTarget.INSTAGRAM))

        assertEquals(listOf(ShareTarget.INSTAGRAM, ShareTarget.OTHER), GetShareTargetsUseCase(files)())
        assertEquals(listOf(ShareTarget.OTHER), GetShareTargetsUseCase(FakeShareCardFiles())())
    }

    @Test
    fun `the map line keeps the coordinates of the trimmed part`() {
        val trimmed = ShareCardLine.trimmed(ride, isEndsHidden = true)

        assertTrue(trimmed.first().distanceM >= ShareCardConfig.HIDDEN_ENDS_M)
        assertTrue(trimmed.last().distanceM <= ride.last().distanceM - ShareCardConfig.HIDDEN_ENDS_M)
        assertEquals(ShareCardLine.of(ride, isEndsHidden = true).size, trimmed.size)
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
