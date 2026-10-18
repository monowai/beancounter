package com.beancounter.marketdata.providers.sec

import com.beancounter.common.model.Asset
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Resolves a Beancounter asset code to the ten-digit CIK that `data.sec.gov` endpoints key on.
 *
 * SEC spells share classes with a hyphen (`BRK-B`) where Beancounter uses a dot (`BRK.B`), so
 * the code is upper-cased and dot-to-hyphen translated before the lookup.
 *
 * A failed index download is not cached, so while SEC throttles us every asset in a refresh run
 * would request the index again and prolong the block. After a 403 or 429 the resolver throws
 * [SecThrottledException] without a request until [THROTTLE_COOLDOWN] has passed.
 */
@Component
class SecTickerResolver(
    private val directory: SecTickerDirectory,
    private val clock: Clock = Clock.systemUTC()
) {
    @Volatile
    private var throttledUntil: Instant = Instant.MIN

    fun resolve(asset: Asset): String? {
        if (clock.instant() < throttledUntil) {
            throw SecThrottledException("SEC index throttled until $throttledUntil")
        }
        val index =
            try {
                directory.tickerToCik()
            } catch (e: HttpStatusCodeException) {
                if (e.statusCode !in THROTTLE_STATUSES) throw e
                throttledUntil = clock.instant().plus(THROTTLE_COOLDOWN)
                throw SecThrottledException("SEC index throttled (${e.statusCode.value()})", e)
            }
        return index[normalise(asset.code)]
    }

    private fun normalise(code: String): String = code.trim().uppercase().replace('.', '-')

    companion object {
        /** SEC's throttle responses: 429, and the 403 "Request Rate Threshold Exceeded" page. */
        val THROTTLE_STATUSES = setOf(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.FORBIDDEN)

        /** SEC's fair-access block lasts about ten minutes. */
        val THROTTLE_COOLDOWN: Duration = Duration.ofMinutes(10)
    }
}