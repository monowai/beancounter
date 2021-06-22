package com.beancounter.marketdata.assets.figi

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.Locale
import kotlin.collections.ArrayList

@Service
@ConditionalOnProperty(value = ["beancounter.marketdata.provider.FIGI.enabled"], matchIfMissing = true)
class FigiProxy internal constructor(figiConfig: FigiConfig) {
    private val figiConfig: FigiConfig
    private val filter: MutableCollection<String> = ArrayList()
    private lateinit var figiGateway: FigiGateway
    private lateinit var figiAdapter: FigiAdapter

    @Autowired
    fun setFigiGateway(figiGateway: FigiGateway) {
        this.figiGateway = figiGateway
    }

    @Autowired
    fun setFigiAdapter(figiAdapter: FigiAdapter) {
        this.figiAdapter = figiAdapter
    }

    @RateLimiter(name = "figi")
    fun find(market: Market, bcAssetCode: String): Asset? {
        val figiCode = bcAssetCode.replace(".", "/").uppercase(Locale.getDefault())
        val figiMarket = market.aliases[FIGI]
        val figiSearch = FigiSearch(
            figiCode,
            figiMarket!!,
            EQUITY,
            true
        )
        val response = resolve(figiSearch)
        if (response?.error != null) {
            log.debug("Error {}/{} {}", figiMarket, figiCode, response.error)
            return if (response.error.equals("No identifier found.", ignoreCase = true)) {
                // Unknown, so don't continue to hit the service - add a name value
                figiAdapter.transform(market, bcAssetCode)
            } else null
        }
        if (response?.data != null) {
            for (datum in response.data!!) {
                if (filter.contains(datum.securityType2.uppercase(Locale.getDefault()))) {
                    log.trace(
                        "In response to {}/{} - found {}/{}",
                        market, bcAssetCode, figiMarket, figiCode
                    )
                    return figiAdapter.transform(market, bcAssetCode, datum)
                }
            }
        }
        log.debug("Couldn't find {}/{} - as {}/{}", market, bcAssetCode, figiMarket, figiCode)
        return null
    }

    private fun resolve(figiSearch: FigiSearch): FigiResponse? {
        var response = findEquity(figiSearch)
        if (response?.error != null) {
            response = findAdr(figiSearch)
            if (response?.error != null) {
                response = findMutualFund(figiSearch)
                if (response?.error != null) {
                    response = findReit(figiSearch)
                }
            }
        }
        return response
    }

    private fun getSearchArgs(figiSearch: FigiSearch): Collection<FigiSearch> {
        val figiSearches: MutableCollection<FigiSearch> = ArrayList()
        figiSearches.add(figiSearch)
        return figiSearches
    }

    private fun extractResult(search: Collection<FigiResponse?>?): FigiResponse? {
        return if (search!!.isEmpty()) {
            null
        } else search.iterator().next()
    }

    private fun findEquity(figiSearch: FigiSearch): FigiResponse? {
        figiSearch.securityType2 = EQUITY
        return extractResult(figiGateway.search(getSearchArgs(figiSearch), figiConfig.apiKey))
    }

    private fun findMutualFund(figiSearch: FigiSearch): FigiResponse? {
        figiSearch.securityType2 = MF
        return extractResult(figiGateway.search(getSearchArgs(figiSearch), figiConfig.apiKey))
    }

    private fun findReit(figiSearch: FigiSearch): FigiResponse? {
        figiSearch.securityType2 = REIT
        return extractResult(figiGateway.search(getSearchArgs(figiSearch), figiConfig.apiKey))
    }

    private fun findAdr(figiSearch: FigiSearch): FigiResponse? {
        figiSearch.securityType2 = ADR
        return extractResult(figiGateway.search(getSearchArgs(figiSearch), figiConfig.apiKey))
    }

    companion object {
        const val EQUITY: String = "Common Stock"
        const val ADR: String = "Depositary Receipt"
        const val REIT: String = "REIT"
        const val FIGI = "FIGI"
        const val MF = "Mutual Fund"
        private val log = LoggerFactory.getLogger(FigiProxy::class.java)
    }

    init {
        log.info("FIGI Enabled")
        this.figiConfig = figiConfig
        filter.add("COMMON STOCK")
        filter.add("REIT")
        filter.add("DEPOSITARY RECEIPT")
        filter.add("MUTUAL FUND")
    }
}
