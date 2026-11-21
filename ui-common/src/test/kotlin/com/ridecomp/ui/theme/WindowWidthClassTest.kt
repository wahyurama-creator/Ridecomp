package com.ridecomp.ui.theme

import androidx.compose.ui.unit.dp
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 13 foundation (T13.3): the width class and the rule that 360 dp and wider phones do not change. */
class WindowWidthClassTest {
    @Test
    fun `below 360 dp is compact, 360 dp and wider is regular`() {
        listOf(319, 320, 359).forEach { assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(it)) }
        listOf(360, 390, 412).forEach { assertEquals(WindowWidthClass.REGULAR, WindowWidthClass.fromWidthDp(it)) }
    }

    @Test
    fun `regular sizes are exactly the existing tokens`() {
        val regular = WindowWidthClass.REGULAR.dimens
        assertEquals(RideCompDimens.cardPaddingHorizontal, regular.cardPaddingHorizontal)
        assertEquals(RideCompDimens.titleSize, regular.messageTitleSize)
        assertEquals(RideCompDimens.pauseTimeSize, regular.pauseTimeSize)
        assertEquals(RideCompDimens.gpsWaitOuter, regular.gpsWaitOuter)
        assertEquals(RideCompDimens.gpsWaitInner, regular.gpsWaitInner)
        assertEquals(RideCompDimens.countdownRing, regular.countdownRing)
        assertEquals(RideCompDimens.displaySize, regular.displaySize)
        assertEquals(RideCompDimens.displayLineHeight, regular.displayLineHeight)
        assertEquals(RideCompDimens.none, regular.statColumnGap)
    }

    @Test
    fun `compact sizes are smaller but stay within the safety limits`() {
        val regular = AdaptiveDimens.Regular
        val compact = WindowWidthClass.COMPACT.dimens
        assertTrue(compact.cardPaddingHorizontal < regular.cardPaddingHorizontal)
        assertTrue(compact.cardPaddingHorizontal >= RideCompDimens.spacingS)
        assertTrue(compact.messageTitleSize.value < regular.messageTitleSize.value)
        assertTrue(compact.messageTitleSize.value >= RideCompDimens.captionSize.value)
        assertTrue(compact.pauseTimeSize.value < regular.pauseTimeSize.value)
        assertTrue(compact.gpsWaitOuter < regular.gpsWaitOuter && compact.gpsWaitInner < compact.gpsWaitOuter)
        assertTrue(compact.gpsWaitInner > RideCompDimens.iconSizeLarge)
        // The countdown (96 sp) and "seconds" still fit inside the ring.
        assertTrue(compact.countdownRing < regular.countdownRing && compact.countdownRing >= COUNTDOWN_RING_MIN)
        assertTrue(compact.displaySize.value < regular.displaySize.value)
        assertTrue(compact.displayLineHeight.value >= compact.displaySize.value)
        assertTrue(compact.statColumnGap > regular.statColumnGap)
    }

    @Test
    fun `every token is checked here`() {
        // A token added to AdaptiveDimens must also be added to the two tests above.
        val tokens = AdaptiveDimens::class.java.declaredFields.count { !Modifier.isStatic(it.modifiers) }
        assertEquals(TOKEN_COUNT, tokens)
    }

    private companion object {
        const val TOKEN_COUNT = 9
        val COUNTDOWN_RING_MIN = 160.dp
    }
}
