package com.beancounter.common

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.AssetUtils.Companion.getJsonAsset
import com.beancounter.common.utils.BcJson.objectMapper
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

internal class TestMarketData {
    private val dateUtils = DateUtils()

    @Test
    @Throws(Exception::class)
    fun is_MarketDataSerializing() {
        val marketDataCollection: MutableCollection<MarketData> = ArrayList()
        val marketData = MarketData(
                null,
                getJsonAsset("Market", "Asset"),
                "TEST",
                dateUtils.getDate("2012-10-01"),
                BigDecimal.ONE,  //Open
                BigDecimal.TEN,  // Close
                BigDecimal.ONE,  // Low
                BigDecimal.TEN,  //High
                BigDecimal("9.56"),  // Previous CLOSE
                BigDecimal("1.56"),  // Change
                BigDecimal("0.04"),  // change %
                10,
                null,
                null)
        marketDataCollection.add(marketData)
        val priceResponse = PriceResponse(marketDataCollection)
        val (data) = objectMapper.readValue(
                objectMapper.writeValueAsString(priceResponse),
                PriceResponse::class.java)
        assertThat(data).isNotNull
        val mdResponse = data.iterator().next()
        compare(marketData, mdResponse)
        assertThat(data.iterator().next().changePercent).isEqualTo("0.04")
    }

    @Test
    @Throws(Exception::class)
    fun is_QuantitiesWorking() {
        val quantityValues = QuantityValues()
        AssertionsForClassTypes.assertThat(quantityValues)
                .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
                .hasFieldOrPropertyWithValue("purchased", BigDecimal.ZERO)
                .hasFieldOrPropertyWithValue("adjustment", BigDecimal.ZERO)
        AssertionsForClassTypes.assertThat(quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO)
        val json = objectMapper.writeValueAsString(quantityValues)
        AssertionsForClassTypes.assertThat(objectMapper.readValue(json, QuantityValues::class.java))
                .usingRecursiveComparison().isEqualTo(quantityValues)
    }

    @Test
    @Throws(Exception::class)
    fun is_PriceRequestSerializing() {
        val assets: MutableCollection<AssetInput> = ArrayList()
        assets.add(getAssetInput("XYZ", "ABC"))
        val priceRequest = PriceRequest("2019-11-11", assets)
        val json = objectMapper.writeValueAsString(priceRequest)
        val (_, assets1) = objectMapper.readValue(json, PriceRequest::class.java)
        AssertionsForClassTypes.assertThat(assets1.iterator().next())
                .usingRecursiveComparison().isEqualTo(
                        priceRequest.assets.iterator().next())
    }

    companion object {
        fun compare(marketData: MarketData, mdResponse: MarketData) {
            AssertionsForClassTypes.assertThat(mdResponse)
                    .isEqualToIgnoringGivenFields(marketData, "asset")
            AssertionsForClassTypes.assertThat(mdResponse.asset.market)
                    .isEqualToIgnoringGivenFields(marketData.asset.market)
            AssertionsForClassTypes.assertThat(mdResponse.asset)
                    .isEqualToIgnoringGivenFields(marketData.asset, "market")
        }
    }
}