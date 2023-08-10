package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.NZX
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.alphaContracts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.io.File
import java.math.BigDecimal

/**
 * Market Data integration with AlphaVantage.co
 *
 * @author mikeh
 * @since 2019-03-03
 */
internal class AlphaBehaviourTest {
    private val dateUtils = DateUtils()
    private val alphaConfig = AlphaConfig()
    private val priceMapper = alphaConfig.getObjectMapper()

    @Test
    fun is_NullAsset() {
        assertThat(
            priceMapper.readValue(
                ClassPathResource("$alphaContracts/alphavantage-empty-response.json").file,
                PriceResponse::class.java,
            ),
        ).isNull()
    }

    @Test
    fun is_GlobalResponse() {
        val marketData = priceMapper.readValue(
            ClassPathResource("$alphaContracts/global-response.json").file,
            PriceResponse::class.java,
        )
        assertThat(marketData)
            .isNotNull
            .hasNoNullFieldsOrPropertiesExcept("id", "requestDate")
        assertThat(marketData.data).isNotNull.isNotEmpty
        assertThat(marketData.data.iterator().next().changePercent).isEqualTo("0.008812")
    }

    @Test
    fun is_CollectionFromResponseReturnedWithDividend() {
        val result = priceMapper.readValue(
            ClassPathResource("$alphaContracts/kmi-backfill-response.json").file,
            PriceResponse::class.java,
        )
        assertThat(result.data).isNotNull.hasSize(4)
        for (marketData in result.data) {
            assertThat(marketData)
                .hasFieldOrProperty("volume")
                .hasFieldOrProperty("dividend")
                .hasFieldOrProperty("split")
            val resolvedDate = dateUtils.getDate("2020-05-01")
            assertThat(resolvedDate).isNotNull
            assertThat(marketData.priceDate).isNotNull
            if (marketData.priceDate!!.compareTo(resolvedDate) == 0) {
                // Dividend
                assertThat(marketData.dividend).isEqualTo(BigDecimal("0.2625"))
            }
        }
    }

    @Test
    fun is_MutualFundGlobalResponse() {
        val marketData = priceMapper.readValue(
            ClassPathResource("$alphaContracts/pence-price-response.json").file,
            PriceResponse::class.java,
        )
        assertThat(marketData)
            .isNotNull
            .hasNoNullFieldsOrPropertiesExcept("id", "requestDate")
    }

    @Test
    fun is_ResponseWithoutMarketCodeSetToUs() {
        val (asset) = validateResponse(
            ClassPathResource("$alphaContracts/alphavantage-nasdaq.json").file,
        )
        assertThat(asset)
            .hasFieldOrPropertyWithValue("code", "NDAQ")
            .hasFieldOrPropertyWithValue("market.code", "US")
    }

    private fun validateResponse(jsonFile: File): MarketData {
        val priceResponse = priceMapper.readValue(jsonFile, PriceResponse::class.java)
        assertThat(priceResponse.data).isNotNull.isNotEmpty
        val marketData = priceResponse.data.iterator().next()
        assertThat(marketData)
            .isNotNull
            .hasFieldOrProperty("asset")
            .hasFieldOrProperty("priceDate")
            .hasFieldOrPropertyWithValue("open", BigDecimal("119.3700"))
            .hasFieldOrPropertyWithValue("high", BigDecimal("121.6100"))
            .hasFieldOrPropertyWithValue("low", BigDecimal("119.2700"))
            .hasFieldOrPropertyWithValue("close", BigDecimal("121.3000"))
            .hasFieldOrPropertyWithValue("volume", BigDecimal("958346").intValueExact())
        return marketData
    }

    @Test
    fun is_KnownMarketVariancesHandled() {
        val alphaPriceService = AlphaPriceService(alphaConfig)
        // No configured support to handle the market
        assertThat(alphaPriceService.isMarketSupported(NZX))
            .isFalse
        assertThat(alphaConfig.getPriceCode(MSFT)).isEqualTo(MSFT.code)
        val ohi = getTestAsset(NYSE, "OHI")
        assertThat(alphaConfig.getPriceCode(ohi)).isEqualTo("OHI")
        val abc = getTestAsset(Market("AMEX"), "ABC")
        assertThat(alphaConfig.getPriceCode(abc)).isEqualTo("ABC")
        val nzx = getTestAsset(NZX, "AIRNZ")
        assertThat(alphaConfig.getPriceCode(nzx)).isEqualTo("AIRNZ.NZX")
    }

    @Test
    fun is_PriceDateAccountingForWeekends() {
        // Sunday
        val computedDate = alphaConfig.getMarketDate(NASDAQ, "2020-04-26")
        // Resolves to Friday
        assertThat(computedDate).isEqualTo(dateUtils.getDate("2020-04-24"))
    }

    @Test
    fun is_PriceDateInThePastConstant() {
        val computedDate = alphaConfig.getMarketDate(NASDAQ, "2020-04-28", false)
        assertThat(computedDate).isEqualTo(dateUtils.getDate("2020-04-28"))
    }
}
