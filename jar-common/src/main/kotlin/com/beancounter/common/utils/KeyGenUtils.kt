package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Builds shorter web-safe keys from a UUID byte array (of exactly 16 bytes), base64 encodes it,
 * using a URL-safe encoding scheme.  The resulting string will be 22 characters in length with no extra
 * padding on the end (e.g. no "==" on the end).
 *
 * Base64 encoding takes each three bytes from the array and converts them into
 * four characters.  This implementation, not using padding, converts the last byte into two
 * characters.
 *
 * @return a URL-safe base64-encoded string.
 */
@Service
class KeyGenUtils {
    /**
     * Given a UUID instance, return a short (22-character) string
     * representation of it.
     *
     * @param uuid a UUID instance.
     * @return a short string representation of the UUID.
     * @throws BusinessException  if the UUID instance is null.
     * @throws IllegalArgumentException if the underlying UUID implementation is not 16 bytes.
     */
    fun format(uuid: UUID?): String {
        val bb =
            ByteBuffer.wrap(ByteArray(16)).apply {
                putLong(uuid?.mostSignificantBits ?: throw BusinessException("Null UUID"))
                putLong(uuid.leastSignificantBits)
            }
        return encodeBase64(bb.array())
    }

    /**
     * Given a UUID representation (either a short or long form), return a
     * UUID from it.
     *
     *
     * If the uuidString is longer than our short, 22-character form (or 24 with padding),
     * it is assumed to be a full-length 36-character UUID string.
     *
     * @param uuidString a string representation of a UUID.
     * @return a UUID instance
     * @throws IllegalArgumentException if the uuidString is not a valid UUID representation.
     * @throws BusinessException     if the uuidString is null.
     */
    fun parse(uuidString: String?): UUID {
        if (uuidString.isNullOrEmpty()) {
            throw BusinessException("Invalid UUID string")
        }
        if (uuidString.length > 24) {
            return UUID.fromString(uuidString)
        }
        if (uuidString.length < 22) {
            throw BusinessException("Short UUID must be 22 characters: $uuidString")
        }
        val bytes = decodeBase64(uuidString)
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.put(
            bytes,
            0,
            16
        )
        bb.clear()
        return UUID(
            bb.long,
            bb.long
        )
    }

    val id: String
        get() = format(UUID.randomUUID())

    companion object {
        private val CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray()
        private val i256 = IntArray(256)

        init {
            for (i in CHARS.indices) {
                i256[CHARS[i].code] = i
            }
        }

        private fun encodeBase64(bytes: ByteArray): String {
            // Output is always 22 characters.
            val chars = CharArray(22)
            var i = 0
            var j = 0
            while (i < 15) {
                // Get the next three bytes.
                val d: Int =
                    bytes[i++].toInt() and 0xff shl 16 or (bytes[i++].toInt() and 0xff shl 8) or
                        (bytes[i++].toInt() and 0xff)

                // Put them in these four characters
                chars[j++] = CHARS[d ushr 18 and 0x3f]
                chars[j++] = CHARS[d ushr 12 and 0x3f]
                chars[j++] = CHARS[d ushr 6 and 0x3f]
                chars[j++] = CHARS[d and 0x3f]
            }

            // The last byte of the input gets put into two characters at the end of the string.
            val d: Int = bytes[i].toInt() and 0xff shl 10
            chars[j++] = CHARS[d shr 12]
            chars[j] = CHARS[d ushr 6 and 0x3f]
            return String(chars)
        }

        private fun decodeBase64(s: String): ByteArray {
            // Output is always 16 bytes (UUID).
            val bytes = ByteArray(16)
            var i = 0
            var j = 0
            while (i < 15) {
                // Get the next four characters.
                val d =
                    i256[s[j++].code] shl 18 or (
                        i256[s[j++].code] shl 12
                    ) or (
                        i256[s[j++].code] shl 6
                    ) or i256[s[j++].code]

                // Put them in these three bytes.
                bytes[i++] = (d shr 16).toByte()
                bytes[i++] = (d shr 8).toByte()
                bytes[i++] = d.toByte()
            }

            // Add the last two characters from the string into the last byte.
            val most = i256[s[j++].code] shl 18
            val least = i256[s[j].code] shl 12

            bytes[i] = (((most or least) shr 16).toByte())
            return bytes
        }
    }
}