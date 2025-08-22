package com.contracts.data

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.cash.CashService
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.alpha.AlphaGateway
import com.beancounter.marketdata.providers.alpha.F_CHANGE
import com.beancounter.marketdata.providers.alpha.F_DATE
import com.beancounter.marketdata.providers.alpha.F_HIGH
import com.beancounter.marketdata.providers.alpha.F_LOW
import com.beancounter.marketdata.providers.alpha.F_OPEN
import com.beancounter.marketdata.providers.alpha.F_PREVIOUS_CLOSE
import com.beancounter.marketdata.providers.alpha.F_PRICE
import com.beancounter.marketdata.providers.alpha.F_VOLUME
import com.fasterxml.jackson.annotation.JsonProperty
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * Base class for Price Contract tests. This is called by the spring cloud contract verifier
 */
class PricesBase : ContractVerifierBase() {
    @MockitoBean
    internal lateinit var alphaGateway: AlphaGateway

    @MockitoBean
    private lateinit var assetRepository: AssetRepository

    @MockitoBean
    private lateinit var assetFinder: AssetFinder

    @MockitoBean
    private lateinit var marketDataRepo: MarketDataRepo

    @MockitoBean
    private lateinit var cashService: CashService

    @MockitoBean
    private lateinit var dateUtils: DateUtils

    @BeforeEach
    fun initAssets() {
        val ebay =
            Asset(
                id = "EBAY",
                code = "EBAY",
                name = "eBay Inc.",
                market =
                    Market("NASDAQ"),
                status = com.beancounter.common.model.Status.Active
            )
        `when`(assetRepository.findAllById(listOf(ebay.id)))
            .thenReturn(listOf(ebay))
        `when`(assetFinder.find(ebay.id))
            .thenReturn(ebay)

        val aapl =
            Asset(
                id = "AAPL",
                code = "AAPL",
                name = "Apple Inc.",
                market =
                    Market("NASDAQ"),
                status = com.beancounter.common.model.Status.Active
            )
        `when`(assetRepository.findAllById(listOf(aapl.id)))
            .thenReturn(listOf(aapl))
        `when`(assetFinder.find(aapl.id))
            .thenReturn(aapl)

        val msft =
            Asset(
                id = "MSFT",
                code = "MSFT",
                name = "Microsoft Corporation",
                market =
                    Market("NASDAQ"),
                status = com.beancounter.common.model.Status.Active
            )
        `when`(assetRepository.findAllById(listOf(msft.id)))
            .thenReturn(listOf(msft))
        `when`(assetFinder.find(msft.id))
            .thenReturn(msft)

        Market("CASH")
        val usdAsset =
            Asset(
                "USD",
                "USD",
                name = "USD Balance",
                category = CASH_MARKET.code,
                priceSymbol = "USD",
                assetCategory =
                    com.beancounter.common.model
                        .AssetCategory("CASH", "Cash"),
                market =
                    Market(
                        code = "CASH",
                        currencyId = "USD",
                        timezoneId = "UTC",
                        timezone = java.util.TimeZone.getTimeZone("UTC"),
                        priceTime = java.time.LocalTime.of(19, 0),
                        daysToSubtract = 1,
                        multiplier = BigDecimal.ONE
                    )
            )
        val nzdAsset =
            Asset(
                "NZD",
                "NZD",
                name = "NZD Balance",
                category = CASH_MARKET.code,
                priceSymbol = "NZD",
                assetCategory =
                    com.beancounter.common.model
                        .AssetCategory("CASH", "Cash"),
                market =
                    Market(
                        code = "CASH",
                        currencyId = "USD",
                        timezoneId = "UTC",
                        timezone = java.util.TimeZone.getTimeZone("UTC"),
                        priceTime = java.time.LocalTime.of(19, 0),
                        daysToSubtract = 1,
                        multiplier = BigDecimal.ONE
                    )
            )
        `when`(
            assetRepository.findAllById(
                listOf(
                    "AAPL",
                    "USD",
                    "NZD"
                )
            )
        ).thenReturn(
            listOf(
                usdAsset,
                nzdAsset,
                aapl
            )
        )
        `when`(assetFinder.find("USD")).thenReturn(usdAsset)
        `when`(assetFinder.find("NZD")).thenReturn(nzdAsset)
        `when`(assetFinder.find("EBAY")).thenReturn(ebay)
        `when`(assetFinder.find("AAPL")).thenReturn(aapl)
        `when`(assetFinder.find("MSFT")).thenReturn(msft)

        // Mock hydrateAsset calls used by AssetService.resolveAssets
        `when`(assetFinder.hydrateAsset(usdAsset)).thenReturn(usdAsset)
        `when`(assetFinder.hydrateAsset(nzdAsset)).thenReturn(nzdAsset)
        `when`(assetFinder.hydrateAsset(ebay)).thenReturn(ebay)
        `when`(assetFinder.hydrateAsset(aapl)).thenReturn(aapl)
        `when`(assetFinder.hydrateAsset(msft)).thenReturn(msft)

        // Mock findLocally calls used by MarketDataService
        `when`(
            assetFinder.findLocally(
                com.beancounter.common.input
                    .AssetInput("NASDAQ", "EBAY")
            )
        ).thenReturn(ebay)
        `when`(
            assetFinder.findLocally(
                com.beancounter.common.input
                    .AssetInput("CASH", "USD")
            )
        ).thenReturn(usdAsset)
        `when`(
            assetFinder.findLocally(
                com.beancounter.common.input
                    .AssetInput("CASH", "NZD")
            )
        ).thenReturn(nzdAsset)
        `when`(
            assetFinder.findLocally(
                com.beancounter.common.input
                    .AssetInput("NASDAQ", "AAPL")
            )
        ).thenReturn(aapl)
        `when`(
            assetFinder.findLocally(
                com.beancounter.common.input
                    .AssetInput("NASDAQ", "MSFT")
            )
        ).thenReturn(msft)

        mockPrices()
        `when`(dateUtils.isToday(anyString())).thenReturn(true)
        `when`(dateUtils.getDate()).thenReturn(DateUtils().getDate())
    }

    fun mockPrices() {
        val open = BigDecimal("39.21")
        val high = BigDecimal("39.35")
        val low = BigDecimal("38.74")
        val close = BigDecimal("100.00")
        val volume = Integer.decode("6274307")
        mockPriceResponse(
            AlphaPriceResponse(
                "EBAY",
                open,
                high,
                low,
                close,
                volume,
                priceDate
            )
        )
        mockPriceResponse(
            AlphaPriceResponse(
                "MSFT",
                open,
                high,
                low,
                close,
                volume,
                priceDate
            )
        )
        mockPriceResponse(
            AlphaPriceResponse(
                "AAPL",
                open,
                high,
                low,
                close,
                volume,
                priceDate
            )
        )
    }

    private fun mockPriceResponse(marketData: AlphaPriceResponse) {
        val response =
            mapOf(
                Pair(
                    "Global Quote",
                    marketData
                )
            )
        `when`(
            alphaGateway
                .getCurrent(
                    marketData.symbol,
                    "demo"
                )
        ).thenReturn(objectMapper.writeValueAsString(response))
    }

    /**
     * Alphavantage price response structure.
     */
    class AlphaPriceResponse(
        @param:JsonProperty("01. symbol")
        val symbol: String,
        @param:JsonProperty(F_OPEN)
        val open: BigDecimal,
        @param:JsonProperty(F_HIGH)
        val high: BigDecimal,
        @param:JsonProperty(F_LOW)
        val low: BigDecimal,
        @param:JsonProperty(F_PRICE)
        val close: BigDecimal,
        @param:JsonProperty(F_VOLUME)
        val volume: Int,
        @param:JsonProperty(F_DATE)
        val priceDate: String,
        @param:JsonProperty(F_PREVIOUS_CLOSE)
        val previousClose: BigDecimal = close,
        @param:JsonProperty(F_CHANGE)
        val changePercent: BigDecimal = BigDecimal.ZERO
    )
}