package com.beancounter.marketdata.assets.figi

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import java.util.Locale

/**
 * Rate limited integration to OpenFigi.
 */
@Service
@ConditionalOnProperty(
    value = ["beancounter.marketdata.provider.figi.enabled"],
    matchIfMissing = true
)
class FigiProxy internal constructor(
    val figiConfig: FigiConfig,
    val figiGateway: FigiGateway,
    val figiAdapter: FigiAdapter
) {
    private val filter: List<String> =
        arrayListOf(
            "COMMON STOCK",
            "REIT",
            "DEPOSITARY RECEIPT",
            "MUTUAL FUND"
        )

    @RateLimiter(name = "figi")
    fun find(
        market: Market,
        bcAssetCode: String,
        id: String = bcAssetCode
    ): Asset? {
        // FIGI resolution needs the market's FIGI exchange alias. A market without
        // one (e.g. a USD-on-LSE listing) can't be FIGI-resolved — return null so
        // the enricher chain falls back to the default enricher instead of NPEing
        // on the alias lookup in resolve() (DATA-5K).
        if (market.aliases[FIGI] == null) {
            log.debug("No FIGI alias for market {} — skipping FIGI enrichment", market.code)
            return null
        }
        val (figiCode, figiMarket, figiSearch) =
            resolve(
                bcAssetCode,
                market
            )
        val response =
            try {
                resolve(figiSearch)
            } catch (e: ResourceAccessException) {
                // DATA-4Z: a transient OpenFIGI I/O blip (api.openfigi.com: Try again) must not
                // abort the caller. Degrade to no enrichment so the chain falls back to the
                // default enricher rather than dead-lettering an async CSV-import message.
                log.warn("FIGI lookup failed for {}/{} - {}; falling back", figiMarket, figiCode, e.message)
                return null
            }
        if (response?.error != null) {
            // Hard error (rate-limit / auth). Return null so the enricher chain falls
            // back to the default enricher rather than persisting a null-named asset.
            log.debug(
                "Error {}/{} {}",
                figiMarket,
                figiCode,
                response.error
            )
            return null
        }
        if (response?.data != null) {
            for (datum in response.data!!) {
                if (filter.contains(datum.securityType2.uppercase(Locale.getDefault()))) {
                    log.trace(
                        "In response to {}/{} - found {}/{}",
                        market,
                        bcAssetCode,
                        figiMarket,
                        figiCode
                    )
                    return figiAdapter.transform(
                        market,
                        bcAssetCode,
                        datum,
                        id
                    )
                }
            }
        }
        log.debug("Couldn't find $market/$bcAssetCode - as $figiMarket/$figiCode")
        return null
    }

    fun resolve(
        bcAssetCode: String,
        market: Market
    ): Triple<String, String, FigiSearch> {
        val figiCode =
            bcAssetCode
                .replace(
                    ".",
                    "/"
                ).uppercase(Locale.getDefault())
        val figiMarket = market.aliases[FIGI]!!
        val figiSearch =
            FigiSearch(
                idValue = figiCode,
                exchCode = figiMarket,
                securityType2 = EQUITY
            )
        return Triple(
            figiCode,
            figiMarket,
            figiSearch
        )
    }

    private fun resolve(figiSearch: FigiSearch): FigiResponse? {
        // Try each securityType in turn. Advance only on a no-match (no data AND no hard
        // error — OpenFIGI v3 signals this with a `warning`). Stop immediately on a match
        // (data != null) or a hard error (error != null, e.g. rate-limit/auth) so the
        // caller can fall back to the default enricher.
        var response = findEquity(figiSearch)
        if (response.isNoMatch()) {
            response = findAdr(figiSearch)
            if (response.isNoMatch()) {
                response = findMutualFund(figiSearch)
                if (response.isNoMatch()) {
                    response = findReit(figiSearch)
                }
            }
        }
        return response
    }

    /**
     * A no-match (keep trying the next securityType): no data and no hard error.
     * Covers the v3 `warning` no-match and a null/empty response.
     */
    private fun FigiResponse?.isNoMatch(): Boolean = this == null || (data == null && error == null)

    private fun getSearchArgs(figiSearch: FigiSearch): Collection<FigiSearch> {
        val figiSearches: MutableCollection<FigiSearch> = ArrayList()
        figiSearches.add(figiSearch)
        return figiSearches
    }

    private fun extractResult(search: Collection<FigiResponse?>?): FigiResponse? =
        if (search!!.isEmpty()) {
            null
        } else {
            search.iterator().next()
        }

    private fun findEquity(figiSearch: FigiSearch): FigiResponse? {
        figiSearch.securityType2 = EQUITY
        return extractResult(
            figiGateway.search(
                getSearchArgs(figiSearch),
                figiConfig.apiKey
            )
        )
    }

    private fun findMutualFund(figiSearch: FigiSearch): FigiResponse? {
        figiSearch.securityType2 = MF
        return extractResult(
            figiGateway.search(
                getSearchArgs(figiSearch),
                figiConfig.apiKey
            )
        )
    }

    private fun findReit(figiSearch: FigiSearch): FigiResponse? {
        figiSearch.securityType2 = REIT
        return extractResult(
            figiGateway.search(
                getSearchArgs(figiSearch),
                figiConfig.apiKey
            )
        )
    }

    private fun findAdr(figiSearch: FigiSearch): FigiResponse? {
        figiSearch.securityType2 = ADR
        return extractResult(
            figiGateway.search(
                getSearchArgs(figiSearch),
                figiConfig.apiKey
            )
        )
    }

    companion object {
        const val EQUITY: String = "Common Stock"
        const val ADR: String = "Depositary Receipt"
        const val REIT: String = "REIT"
        const val FIGI = "FIGI"
        const val MF = "Mutual Fund"
        private val log = LoggerFactory.getLogger(FigiProxy::class.java)
    }
}