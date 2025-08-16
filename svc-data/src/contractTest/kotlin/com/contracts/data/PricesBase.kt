package com.contracts.data

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.MSFT
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
    private lateinit var marketDataRepo: MarketDataRepo

    @MockitoBean
    private lateinit var cashService: CashService

    @MockitoBean
    private lateinit var dateUtils: DateUtils

    @BeforeEach
    fun initAssets() {
        val ebay =
            AAPL.copy(
                id = "EBAY",
                code = "EBAY",
                name = "eBay Inc."
            )
        `when`(assetRepository.findAllById(listOf(ebay.id)))
            .thenReturn(listOf(ebay))

        `when`(assetRepository.findAllById(listOf(AAPL.id)))
            .thenReturn(listOf(AAPL))

        `when`(assetRepository.findAllById(listOf(MSFT.id)))
            .thenReturn(listOf(MSFT))

        val cashMarket = Market("CASH")
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
                Asset(
                    "USD",
                    "USD",
                    category = CASH_MARKET.code,
                    priceSymbol = "USD",
                    name = "USD Balance",
                    market = cashMarket
                ),
                Asset(
                    "NZD",
                    "NZD",
                    category = CASH_MARKET.code,
                    priceSymbol = "NZD",
                    name = "NZD Balance",
                    market = cashMarket
                ),
                AAPL
            )
        )
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
                rateDate
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
                rateDate
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
                rateDate
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