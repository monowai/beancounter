package com.beancounter.marketdata.apikey

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat

/**
 * Generates BC API keys: `bc_` followed by 32 base62 characters from
 * [SecureRandom]. [Generated.prefix] is the first 11 characters of the raw
 * key (`bc_` + the first 8 secret characters) - safe to display, since it
 * carries none of the entropy needed to guess the rest of the key.
 * [Generated.keyHash] is the SHA-256 hex digest of the full raw key; only
 * the hash is ever persisted.
 *
 * A small, injectable Spring bean (rather than a top-level object) so
 * [ApiKeyService] can take it as a constructor dependency.
 */
@Component
class ApiKeyGenerator(
    private val random: SecureRandom = SecureRandom()
) {
    data class Generated(
        val apiKey: String,
        val prefix: String,
        val keyHash: String
    )

    fun generate(): Generated {
        val randomPart = randomBase62(SECRET_LENGTH)
        val apiKey = "$KEY_PREFIX$randomPart"
        return Generated(
            apiKey = apiKey,
            prefix = apiKey.take(PREFIX_DISPLAY_LENGTH),
            keyHash = hash(apiKey)
        )
    }

    private fun randomBase62(length: Int): String =
        buildString(length) {
            repeat(length) {
                append(BASE62_ALPHABET[random.nextInt(BASE62_ALPHABET.length)])
            }
        }

    companion object {
        private const val KEY_PREFIX = "bc_"
        private const val SECRET_LENGTH = 32
        private const val PREFIX_DISPLAY_LENGTH = 11
        private const val BASE62_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        /**
         * SHA-256 hex digest of a raw key. Used both to hash a freshly
         * generated key and, at verification time, to hash a caller-supplied
         * raw key before the [ApiKeyRepository.findByKeyHash] lookup.
         */
        fun hash(rawKey: String): String {
            val digest =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(rawKey.toByteArray(Charsets.UTF_8))
            return HexFormat.of().formatHex(digest)
        }
    }
}