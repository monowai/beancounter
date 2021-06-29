package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.marketdata.utils.AlphaMockUtils
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * Market Data integration with AlphaVantage.co
 *
 * @author mikeh
 * @since 2019-03-03
 */
internal class AlphaApiAssetTest {
    private val assetMapper = AlphaPriceAdapter().alphaMapper

    @Test
    @Throws(Exception::class)
    fun is_NullAsset() {
        val jsonFile = ClassPathResource(
            AlphaMockUtils.alphaContracts +
                "/alphavantage-empty-response.json"
        ).file
        Assertions.assertThat(assetMapper.readValue(jsonFile, PriceResponse::class.java)).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun is_SearchResult() {
        val jsonFile = ClassPathResource(
            AlphaMockUtils.alphaContracts +
                "/mf-search.json"
        ).file
        val searchResponse = assetMapper.readValue(jsonFile, AssetSearchResponse::class.java)
        Assertions.assertThat(searchResponse)
            .isNotNull
            .hasFieldOrProperty("data")
        Assertions.assertThat(searchResponse.data)
            .hasSize(1)
        Assertions.assertThat(searchResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue("name", "AXA Framlington Health Fund Z GBP Acc")
            .hasFieldOrPropertyWithValue("type", "Mutual Fund")
            .hasFieldOrPropertyWithValue("symbol", "0P0000XMSV.LON")
    }
}
