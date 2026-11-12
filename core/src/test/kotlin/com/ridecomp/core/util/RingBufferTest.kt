package com.ridecomp.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingBufferTest {
    @Test
    fun `new buffer is empty`() {
        val buffer = RingBuffer<Int>(3)

        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)
        assertNull(buffer.oldestOrNull())
        assertNull(buffer.newestOrNull())
    }

    @Test
    fun `adding below capacity keeps insertion order and evicts nothing`() {
        val buffer = RingBuffer<Int>(3)

        val evicted = listOf(1, 2).map { buffer.add(it) }

        assertEquals(listOf(null, null), evicted)
        assertEquals(listOf(1, 2), buffer.toList())
        assertFalse(buffer.isFull)
    }

    @Test
    fun `adding when full evicts the oldest element`() {
        val buffer = RingBuffer<Int>(3)
        listOf(1, 2, 3).forEach { buffer.add(it) }

        val evicted = buffer.add(4)

        assertEquals(1, evicted)
        assertEquals(listOf(2, 3, 4), buffer.toList())
        assertEquals(3, buffer.size)
        assertEquals(2, buffer.oldestOrNull())
        assertEquals(4, buffer.newestOrNull())
    }

    @Test
    fun `size never exceeds capacity over many additions`() {
        val buffer = RingBuffer<Int>(5)

        repeat(10_000) { buffer.add(it) }

        assertEquals(5, buffer.size)
        assertEquals(listOf(9_995, 9_996, 9_997, 9_998, 9_999), buffer.toList())
    }

    @Test
    fun `iterator walks from oldest to newest after wrap around`() {
        val buffer = RingBuffer<String>(2)
        listOf("a", "b", "c").forEach { buffer.add(it) }

        assertEquals(listOf("b", "c"), buffer.iterator().asSequence().toList())
    }

    @Test
    fun `get outside bounds throws`() {
        val buffer = RingBuffer<Int>(2)
        buffer.add(1)

        assertFailsWith<IndexOutOfBoundsException> { buffer[1] }
        assertFailsWith<IndexOutOfBoundsException> { buffer[-1] }
    }

    @Test
    fun `exhausted iterator throws`() {
        val iterator = RingBuffer<Int>(1).iterator()

        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun `clear empties the buffer and allows reuse`() {
        val buffer = RingBuffer<Int>(2)
        listOf(1, 2, 3).forEach { buffer.add(it) }

        buffer.clear()
        buffer.add(9)

        assertEquals(listOf(9), buffer.toList())
    }

    @Test
    fun `capacity must be positive`() {
        assertFailsWith<IllegalArgumentException> { RingBuffer<Int>(0) }
    }
}
