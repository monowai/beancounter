package com.contracts.data

import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.alpha.AlphaGateway
import com.beancounter.marketdata.providers.alpha.fChange
import com.beancounter.marketdata.providers.alpha.fDate
import com.beancounter.marketdata.providers.alpha.fHigh
import com.beancounter.marketdata.providers.alpha.fLow
import com.beancounter.marketdata.providers.alpha.fOpen
import com.beancounter.marketdata.providers.alpha.fPreviousClose
import com.beancounter.marketdata.providers.alpha.fPrice
import com.beancounter.marketdata.providers.alpha.fVolume
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
        mockPriceResponse(
            AlphaPriceResponse(
                "EBAY",
                BigDecimal("39.21"),
                BigDecimal("39.35"),
                BigDecimal("38.74"),
                BigDecimal("100.00"),
                Integer.decode("6274307"),
                rateDate,
            ),
        )
        mockPriceResponse(
            AlphaPriceResponse(
                "MSFT",
                BigDecimal("39.21"),
                BigDecimal("39.35"),
                BigDecimal("38.74"),
                BigDecimal("100.00"),
                Integer.decode("6274307"),
                rateDate,
            ),
        )
        mockPriceResponse(
            AlphaPriceResponse(
                "AAPL",
                BigDecimal("39.21"),
                BigDecimal("39.35"),
                BigDecimal("38.74"),
                BigDecimal("100.00"),
                Integer.decode("6274307"),
                rateDate,
            ),
        )
//        mockPriceResponse(
//            AlphaPriceResponse(
//                "EBAY",
//                BigDecimal("39.21"),
//                BigDecimal("39.35"),
//                BigDecimal("38.74"),
//                BigDecimal("100.00"),
//                Integer.decode("6274307"),
//                "2021-10-19"
//            )
//        )
    }

    private fun mockPriceResponse(marketData: AlphaPriceResponse) {
        val response = mapOf(Pair("Global Quote", marketData))
        Mockito.`when`(
            alphaGateway
                .getCurrent(marketData.symbol, "demo"),
        ).thenReturn(BcJson().objectMapper.writeValueAsString(response))
    }

    class AlphaPriceResponse(
        @JsonProperty("01. symbol")
        val symbol: String,
        @JsonProperty(fOpen)
        val open: BigDecimal,
        @JsonProperty(fHigh)
        val high: BigDecimal,
        @JsonProperty(fLow)
        val low: BigDecimal,
        @JsonProperty(fPrice)
        val close: BigDecimal,
        @JsonProperty(fVolume)
        val volume: Int,
        @JsonProperty(fDate)
        val priceDate: String,
        @JsonProperty(fPreviousClose)
        val previousClose: BigDecimal = close,
        @JsonProperty(fChange)
        val changePercent: BigDecimal = BigDecimal.ZERO,
    )
}
