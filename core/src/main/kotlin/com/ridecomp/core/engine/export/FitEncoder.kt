package com.ridecomp.core.engine.export

/** FIT base types used by RideComp (FIT SDK Profile). [invalid] is written when a value is missing. */
public enum class FitBaseType(public val id: Int, public val size: Int, public val invalid: Long) {
    ENUM(0x00, 1, 0xFF),
    SINT8(0x01, 1, 0x7F),
    UINT8(0x02, 1, 0xFF),
    SINT16(0x83, 2, 0x7FFF),
    UINT16(0x84, 2, 0xFFFF),
    SINT32(0x85, 4, 0x7FFF_FFFF),
    UINT32(0x86, 4, 0xFFFF_FFFFL),
    UINT32Z(0x8C, 4, 0x0000_0000),
}

/** One field of a message definition. */
public data class FitField(val number: Int, val type: FitBaseType)

/** A message definition bound to a local message type. */
public data class FitMessageDefinition(val localType: Int, val globalNumber: Int, val fields: List<FitField>) {
    /** Bytes of one data message of this definition (header + fields). */
    public val dataSize: Int get() = 1 + fields.sumOf { it.type.size }
}

/** CRC-16 used by FIT for the header and the whole file (FIT protocol). */
public object FitCrc {
    private val table = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )
    private const val NIBBLE_MASK = 0xF
    private const val NIBBLE_BITS = 4
    private const val LOW_12_BITS = 0x0FFF
    private const val BYTE_MASK = 0xFF

    public fun update(crc: Int, byte: Int): Int {
        val value = byte and BYTE_MASK
        var result = crc
        var tmp = table[result and NIBBLE_MASK]
        result = (result shr NIBBLE_BITS) and LOW_12_BITS
        result = result xor tmp xor table[value and NIBBLE_MASK]
        tmp = table[result and NIBBLE_MASK]
        result = (result shr NIBBLE_BITS) and LOW_12_BITS
        return result xor tmp xor table[(value shr NIBBLE_BITS) and NIBBLE_MASK]
    }

    public fun of(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Int =
        (from until to).fold(0) { crc, index -> update(crc, bytes[index].toInt()) }
}

/**
 * Low-level FIT writer (04-INTEGRATIONS §B): little-endian definition and data messages into a growable
 * buffer; [toFile] adds the 14-byte header and the file CRC. Values are passed as `Long?`; null is written
 * as the base type's invalid value (never 0, §B.2).
 */
public class FitEncoder {
    private var data = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    /** Writes a definition message (little-endian architecture). */
    public fun define(definition: FitMessageDefinition) {
        writeByte(DEFINITION_FLAG or definition.localType)
        writeByte(RESERVED)
        writeByte(LITTLE_ENDIAN)
        writeInt(definition.globalNumber.toLong(), UINT16_BYTES)
        writeByte(definition.fields.size)
        definition.fields.forEach { field ->
            writeByte(field.number)
            writeByte(field.type.size)
            writeByte(field.type.id)
        }
    }

    /** Writes a data message; [values] must be in the order of the definition's fields. */
    public fun write(definition: FitMessageDefinition, values: List<Long?>) {
        require(values.size == definition.fields.size) { "expected ${definition.fields.size} values" }
        writeByte(definition.localType)
        definition.fields.zip(values).forEach { (field, value) ->
            writeInt(value ?: field.type.invalid, field.type.size)
        }
    }

    /** The complete .fit file: header (with header CRC) + data + file CRC. */
    public fun toFile(profileVersion: Int = PROFILE_VERSION): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        header[HEADER_SIZE_OFFSET] = HEADER_SIZE.toByte()
        header[PROTOCOL_OFFSET] = PROTOCOL_VERSION.toByte()
        putInt(header, PROFILE_OFFSET, profileVersion.toLong(), UINT16_BYTES)
        putInt(header, DATA_SIZE_OFFSET, size.toLong(), UINT32_BYTES)
        FILE_TYPE_MAGIC.forEachIndexed { index, char -> header[HEADER_MAGIC_OFFSET + index] = char.code.toByte() }
        putInt(header, HEADER_CRC_OFFSET, FitCrc.of(header, 0, HEADER_CRC_OFFSET).toLong(), UINT16_BYTES)
        val file = ByteArray(HEADER_SIZE + size + CRC_SIZE)
        header.copyInto(file)
        data.copyInto(file, HEADER_SIZE, 0, size)
        putInt(file, HEADER_SIZE + size, FitCrc.of(file, 0, HEADER_SIZE + size).toLong(), CRC_SIZE)
        return file
    }

    private fun writeByte(value: Int) {
        ensureCapacity(1)
        data[size++] = value.toByte()
    }

    private fun writeInt(value: Long, byteCount: Int) {
        ensureCapacity(byteCount)
        putInt(data, size, value, byteCount)
        size += byteCount
    }

    private fun ensureCapacity(extra: Int) {
        if (size + extra > data.size) data = data.copyOf(maxOf(data.size * GROWTH_FACTOR, size + extra))
    }

    private fun putInt(target: ByteArray, offset: Int, value: Long, byteCount: Int) {
        repeat(byteCount) { index -> target[offset + index] = (value shr (index * BITS_PER_BYTE)).toByte() }
    }

    public companion object {
        public const val HEADER_SIZE: Int = 14
        public const val CRC_SIZE: Int = 2

        /** FIT protocol 1.0 is enough: no developer fields are used. */
        public const val PROTOCOL_VERSION: Int = 0x10

        /** FIT profile 21.00. */
        public const val PROFILE_VERSION: Int = 2_100
        public const val FILE_TYPE_MAGIC: String = ".FIT"
        private const val HEADER_SIZE_OFFSET = 0
        private const val PROTOCOL_OFFSET = 1
        private const val PROFILE_OFFSET = 2
        private const val DATA_SIZE_OFFSET = 4
        private const val HEADER_MAGIC_OFFSET = 8
        private const val UINT16_BYTES = 2
        private const val UINT32_BYTES = 4
        private const val HEADER_CRC_OFFSET = 12
        private const val DEFINITION_FLAG = 0x40
        private const val RESERVED = 0
        private const val LITTLE_ENDIAN = 0
        private const val BITS_PER_BYTE = 8
        private const val INITIAL_CAPACITY = 64 * 1_024
        private const val GROWTH_FACTOR = 2
    }
}
