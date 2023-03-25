package com.beancounter.common

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.utils.AssetUtils.Companion.getJsonAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class TestMarketData {
    private val dateUtils = DateUtils()
    private val bcJson = BcJson()

    @Test
    @Throws(Exception::class)
    fun is_MarketDataSerializing() {
        val marketDataCollection: MutableCollection<MarketData> = ArrayList()
        val marketData = MarketData(
            null,
            getJsonAsset("Market", "Asset"),
            "TEST",
            dateUtils.getDate("2012-10-01"),
            BigDecimal.ONE, // Open
            BigDecimal.TEN, // Close
            BigDecimal.ONE, // Low
            BigDecimal.TEN, // High
            BigDecimal("9.56"), // Previous CLOSE
            BigDecimal("1.56"), // Change
            BigDecimal("0.04"), // change %
            10,
        )
        marketDataCollection.add(marketData)
        val priceResponse = PriceResponse(marketDataCollection)
        val (data) = bcJson.objectMapper.readValue(
            bcJson.objectMapper.writeValueAsString(priceResponse),
            PriceResponse::class.java,
        )
        assertThat(data).isNotNull
        val mdResponse = data.iterator().next()
        compare(mdResponse)
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
        val json = bcJson.objectMapper.writeValueAsString(quantityValues)
        assertThat(bcJson.objectMapper.readValue(json, QuantityValues::class.java))
            .usingRecursiveComparison().isEqualTo(quantityValues)
    }

    @Test
    @Throws(Exception::class)
    fun is_PriceRequestSerializing() {
        val priceRequest = PriceRequest("2019-11-11", arrayListOf(PriceAsset("XYZ", "ABC")))
        val json = bcJson.objectMapper.writeValueAsString(priceRequest)
        val (_, assets) = bcJson.objectMapper.readValue(json, PriceRequest::class.java)
        assertThat(assets.iterator().next())
            .usingRecursiveComparison().isEqualTo(
                priceRequest.assets.iterator().next(),
            )
    }

    companion object {
        fun compare(mdResponse: MarketData) {
            assertThat(mdResponse)
                .usingRecursiveComparison().ignoringFields("marketData", "asset")
            assertThat(mdResponse.asset.market)
                .usingRecursiveComparison().ignoringFields("marketData.asset.market")
            assertThat(mdResponse.asset)
                .usingRecursiveComparison().ignoringFields("marketData.asset", "market")
        }
    }
}
