package com.ridecomp.core.engine.export

/** One decoded data message: global number and raw field values by field number. */
data class FitMessage(val global: Int, val fields: Map<Int, Long>)

/**
 * Minimal FIT reader for tests: validates header size, ".FIT", header CRC and file CRC, then decodes
 * little-endian definition and data messages (no compressed timestamps or developer fields).
 */
object FitDecoder {
    fun decode(file: ByteArray): List<FitMessage> {
        require(file[0].toInt() == FitEncoder.HEADER_SIZE) { "header size" }
        require(String(file, 8, 4, Charsets.US_ASCII) == FitEncoder.FILE_TYPE_MAGIC) { "magic" }
        require(read(file, 12, 2) == FitCrc.of(file, 0, 12).toLong()) { "header CRC" }
        val dataSize = read(file, 4, 4).toInt()
        require(file.size == FitEncoder.HEADER_SIZE + dataSize + FitEncoder.CRC_SIZE) { "data size" }
        require(FitCrc.of(file) == 0) { "file CRC" }
        val definitions = mutableMapOf<Int, Pair<Int, List<Pair<Int, Int>>>>()
        val messages = mutableListOf<FitMessage>()
        var offset = FitEncoder.HEADER_SIZE
        val end = FitEncoder.HEADER_SIZE + dataSize
        while (offset < end) {
            val header = file[offset++].toInt() and 0xFF
            val local = header and 0x0F
            if (header and 0x40 != 0) {
                offset += 2
                val global = read(file, offset, 2).toInt()
                offset += 2
                val count = file[offset++].toInt() and 0xFF
                val fields = List(count) {
                    val number = file[offset].toInt() and 0xFF
                    val size = file[offset + 1].toInt() and 0xFF
                    offset += 3
                    number to size
                }
                definitions[local] = global to fields
            } else {
                val (global, fields) = requireNotNull(definitions[local]) { "undefined local type $local" }
                val values = fields.associate { (number, size) ->
                    number to
                        read(file, offset, size).also { offset += size }
                }
                messages += FitMessage(global, values)
            }
        }
        return messages
    }

    private fun read(bytes: ByteArray, offset: Int, size: Int): Long =
        (0 until size).fold(0L) { acc, i -> acc or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i)) }

    /** Interprets a 32-bit raw value as signed. */
    fun signed32(raw: Long): Int = raw.toInt()
}
