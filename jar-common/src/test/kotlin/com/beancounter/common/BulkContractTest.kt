package com.beancounter.common

import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BulkContractTest {
    private val market =
        com.beancounter.common.model
            .Market("NYSE")

    @Test
    fun `BulkPriceRequest serializes and deserializes`() {
        val request =
            BulkPriceRequest(
                dates = listOf("2024-01-15", "2024-02-15"),
                assets =
                    listOf(
                        PriceAsset(market = "NYSE", code = "MSFT"),
                        PriceAsset(market = "NASDAQ", code = "AAPL")
                    )
            )
        val json = objectMapper.writeValueAsString(request)
        val fromJson = objectMapper.readValue<BulkPriceRequest>(json)

        assertThat(fromJson.dates).hasSize(2).containsExactly("2024-01-15", "2024-02-15")
        assertThat(fromJson.assets).hasSize(2)
        assertThat(fromJson).isEqualTo(request)
    }

    @Test
    fun `BulkPriceResponse serializes and deserializes`() {
        val asset = AssetUtils.getTestAsset(market, "MSFT")
        val md = MarketData(asset = asset, priceDate = LocalDate.of(2024, 1, 15))
        val response = BulkPriceResponse(data = mapOf("2024-01-15" to listOf(md)))

        val json = objectMapper.writeValueAsString(response)
        val fromJson = objectMapper.readValue<BulkPriceResponse>(json)

        assertThat(fromJson.data).containsKey("2024-01-15")
        assertThat(fromJson.data["2024-01-15"]).hasSize(1)
    }

    @Test
    fun `BulkFxRequest serializes and deserializes`() {
        val request =
            BulkFxRequest(
                startDate = "2024-01-01",
                endDate = "2024-03-01",
                pairs = setOf(IsoCurrencyPair("NZD", "USD"), IsoCurrencyPair("GBP", "USD"))
            )
        val json = objectMapper.writeValueAsString(request)
        val fromJson = objectMapper.readValue<BulkFxRequest>(json)

        assertThat(fromJson.startDate).isEqualTo("2024-01-01")
        assertThat(fromJson.endDate).isEqualTo("2024-03-01")
        assertThat(fromJson.pairs).hasSize(2)
        assertThat(fromJson).isEqualTo(request)
    }

    @Test
    fun `BulkFxResponse serializes and deserializes`() {
        val response =
            BulkFxResponse(
                data = mapOf("2024-01-15" to FxPairResults())
            )
        val json = objectMapper.writeValueAsString(response)
        val fromJson = objectMapper.readValue<BulkFxResponse>(json)

        assertThat(fromJson.data).containsKey("2024-01-15")
    }

    @Test
    fun `BulkPriceResponse with empty data`() {
        val response = BulkPriceResponse()
        assertThat(response.data).isEmpty()
    }

    @Test
    fun `BulkFxResponse with empty data`() {
        val response = BulkFxResponse()
        assertThat(response.data).isEmpty()
    }
}