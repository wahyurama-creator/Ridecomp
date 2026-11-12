package com.ridecomp.core.util

/**
 * Fixed-capacity FIFO buffer (MEM-05). When full, adding an element overwrites the oldest one,
 * so memory use never grows during a long ride.
 *
 * Not thread-safe: confine each instance to one coroutine (CONC-01).
 */
public class RingBuffer<T>(public val capacity: Int) : Iterable<T> {
    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private val items = arrayOfNulls<Any?>(capacity)
    private var head = 0

    /** Number of elements currently stored (≤ [capacity]). */
    public var size: Int = 0
        private set

    public val isEmpty: Boolean get() = size == 0
    public val isFull: Boolean get() = size == capacity

    /** Adds [element]; returns the evicted oldest element when the buffer was full, otherwise null. */
    public fun add(element: T): T? {
        val tail = (head + size) % capacity
        return if (isFull) {
            val evicted = elementAtIndex(head)
            items[head] = element
            head = (head + 1) % capacity
            evicted
        } else {
            items[tail] = element
            size++
            null
        }
    }

    /** Element at logical [index] (0 = oldest). */
    public operator fun get(index: Int): T {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index $index, size $size")
        return elementAtIndex((head + index) % capacity)
    }

    /** Oldest element, or null when empty. */
    public fun oldestOrNull(): T? = if (isEmpty) null else get(0)

    /** Newest element, or null when empty. */
    public fun newestOrNull(): T? = if (isEmpty) null else get(size - 1)

    /** Removes every element. */
    public fun clear() {
        items.fill(null)
        head = 0
        size = 0
    }

    /** Snapshot from oldest to newest. */
    public fun toList(): List<T> = List(size) { get(it) }

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var index = 0

        override fun hasNext(): Boolean = index < size

        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++)
        }
    }

    @Suppress("UNCHECKED_CAST") // Slots in [0, size) always hold a T.
    private fun elementAtIndex(physicalIndex: Int): T = items[physicalIndex] as T
}
