package com.contracts.data

import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetService
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
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal

/**
 * Base class for Price Contract tests. This is called by the spring cloud contract verifier
 */
class PricesBase : ContractVerifierBase() {
    @MockBean
    internal lateinit var alphaGateway: AlphaGateway

    @MockBean
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var dateUtils: DateUtils

    @BeforeEach
    fun initAssets() {
        AssetsBase().mockAssets(assetService)
        mockPrices()
        Mockito.`when`(dateUtils.isToday(anyString())).thenReturn(true)
        Mockito.`when`(dateUtils.getLocalDate()).thenReturn(DateUtils().getLocalDate())
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
                rateDate,
            ),
        )
        mockPriceResponse(
            AlphaPriceResponse(
                "MSFT",
                open,
                high,
                low,
                close,
                volume,
                rateDate,
            ),
        )
        mockPriceResponse(
            AlphaPriceResponse(
                "AAPL",
                open,
                high,
                low,
                close,
                volume,
                rateDate,
            ),
        )
    }

    private fun mockPriceResponse(marketData: AlphaPriceResponse) {
        val response = mapOf(Pair("Global Quote", marketData))
        Mockito.`when`(
            alphaGateway
                .getCurrent(marketData.symbol, "demo"),
        ).thenReturn(BcJson().objectMapper.writeValueAsString(response))
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
        val changePercent: BigDecimal = BigDecimal.ZERO,
    )
}
