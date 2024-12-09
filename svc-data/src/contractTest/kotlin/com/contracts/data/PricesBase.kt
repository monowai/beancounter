package com.contracts.data

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.assets.AssetRepository
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
import com.beancounter.marketdata.trn.cash.CashBalancesBean
import com.fasterxml.jackson.annotation.JsonProperty
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal

/**
 * Base class for Price Contract tests. This is called by the spring cloud contract verifier
 */
class PricesBase : ContractVerifierBase() {
    @MockBean
    internal lateinit var alphaGateway: AlphaGateway

    @MockBean
    private lateinit var assetRepository: AssetRepository

    @MockBean
    private lateinit var marketDataRepo: MarketDataRepo

    @MockBean
    private lateinit var cashBalancesBean: CashBalancesBean

    @MockBean
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
        @JsonProperty("01. symbol")
        val symbol: String,
        @JsonProperty(F_OPEN)
        val open: BigDecimal,
        @JsonProperty(F_HIGH)
        val high: BigDecimal,
        @JsonProperty(F_LOW)
        val low: BigDecimal,
        @JsonProperty(F_PRICE)
        val close: BigDecimal,
        @JsonProperty(F_VOLUME)
        val volume: Int,
        @JsonProperty(F_DATE)
        val priceDate: String,
        @JsonProperty(F_PREVIOUS_CLOSE)
        val previousClose: BigDecimal = close,
        @JsonProperty(F_CHANGE)
        val changePercent: BigDecimal = BigDecimal.ZERO
    )
}