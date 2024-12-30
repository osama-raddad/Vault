package com.vynatix

import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.random.Random

class UUID {
    private val uuid: String

    init {
        val data = ByteArray(16).apply {
            Random.nextBytes(this)
            // Set version to 4 (random UUID)
            // Convert 0x40 to Byte using toByte()
            this[6] = ((this[6] and 0x0f) or 0x40)
            // Set variant to RFC 4122
            // Convert both operands to Byte
            this[8] = ((this[8] and 0x3f.toByte()) or 0x80.toByte())
        }

        uuid = buildString {
            data.forEachIndexed { index, byte ->
                append(byte.toUByte().toString(16).padStart(2, '0'))
                when (index) {
                    3, 5, 7, 9 -> append('-')
                }
            }
        }
    }

    override fun toString(): String = uuid

    companion object {
        fun randomUUID(): UUID = UUID()
    }
}