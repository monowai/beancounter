package com.beancounter.marketdata.providers.morningstar

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Morningstar price provider for UK/EU mutual funds.
 * Fetches NAV prices from Morningstar's unofficial API.
 */
@Service
class MorningstarPriceService(
    private val morningstarConfig: MorningstarConfig
) : MarketDataPriceProvider {
    private lateinit var morningstarProxy: MorningstarProxy

    companion object {
        const val ID = "MORNINGSTAR"
        private val log = LoggerFactory.getLogger(MorningstarPriceService::class.java)
    }

    @Autowired(required = false)
    fun setMorningstarProxy(proxy: MorningstarProxy) {
        this.morningstarProxy = proxy
    }

    @PostConstruct
    fun logStatus() {
        log.info("Morningstar provider initialized for markets: {}", morningstarConfig.markets)
    }

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> =
        priceRequest.assets.mapNotNull { it.resolvedAsset }.mapNotNull { fetchPrice(it) }

    /**
     * Fetch and parse a single asset's price. Returns null (with a logged
     * warning) on fetch/parse failure so other assets keep processing.
     */
    private fun fetchPrice(resolvedAsset: Asset): MarketData? {
        val priceCode = morningstarConfig.getPriceCode(resolvedAsset)
        val currencyId = resolvedAsset.market.currency.code
        return try {
            val json = morningstarProxy.getPrice(priceCode, currencyId) ?: return null
            MorningstarAdapter.latestPrice(json, resolvedAsset)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Error fetching price for {}: {}", priceCode, e.message)
            null
        }
    }

    override fun getId(): String = ID

    override fun isMarketSupported(market: Market): Boolean = morningstarConfig.isMarketSupported(market)

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate = morningstarConfig.getMarketDate(market, priceRequest.date, priceRequest.currentMode)

    override fun backFill(
        asset: Asset,
        fromDate: LocalDate
    ): PriceResponse {
        val priceCode = morningstarConfig.getPriceCode(asset)
        val currencyId = asset.market.currency.code

        val json = morningstarProxy.getPrice(priceCode, currencyId, fromDate) ?: return PriceResponse(emptyList())

        return PriceResponse(MorningstarAdapter.history(json, asset))
    }

    override fun isApiSupported(): Boolean = true
}