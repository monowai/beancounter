package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NZX
import com.beancounter.marketdata.providers.DatedBatch
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.marketstack.model.MarketStackData
import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Test MarketStack responses are transformed into MarketData.
 */
class MarketStackAdapterTest {
    private val marketStackAdapter = MarketStackAdapter()

    @Test
    fun `transform should return correct MarketData collection`() {
        val providerArguments = Mockito.mock(ProviderArguments::class.java)
        val batchId = 1
        val apple =
            Asset(
                "AAPL",
                market = NASDAQ
            )
        val microsoft =
            Asset(
                "MSFT",
                market = NASDAQ
            )

        val datedBatch =
            DatedBatch(
                batchId,
                "today"
            )
        val appleData = createMarketStackData(apple.code)
        val msData = createMarketStackData(microsoft.code)

        val response =
            MarketStackResponse(
                data =
                    listOf(
                        appleData,
                        msData
                    )
            )

        `when`(providerArguments.getAssets(batchId)).thenReturn(
            listOf(
                apple.code,
                microsoft.code
            )
        )
        `when`(providerArguments.getAsset(apple.code)).thenReturn(apple)
        `when`(providerArguments.getAsset(microsoft.code)).thenReturn(microsoft)
        `when`(providerArguments.getBatchConfigs(batchId)).thenReturn(datedBatch)

        val result =
            marketStackAdapter.toMarketData(
                providerArguments,
                batchId,
                response
            )

        assertEquals(
            2,
            result.size
        )
        result.forEach { marketData ->
            val expectedData = if (marketData.asset == apple) appleData else msData
            assertEquals(
                expectedData.date.toLocalDate(),
                marketData.priceDate
            )
            assertEquals(
                expectedData.close,
                marketData.close
            )
            assertEquals(
                expectedData.open,
                marketData.open
            )
            assertEquals(
                expectedData.high,
                marketData.high
            )
            assertEquals(
                expectedData.low,
                marketData.low
            )
            assertEquals(
                expectedData.volume,
                marketData.volume
            )
        }
    }

    @Test
    fun `dividend and split fields are mapped from MarketStack response`() {
        val providerArguments = Mockito.mock(ProviderArguments::class.java)
        val batchId = 1
        val gne = Asset("GNE", market = NZX)

        val dividendRate = BigDecimal("0.0717")
        val splitFactor = BigDecimal("2.0")
        val gneData =
            MarketStackData(
                date = LocalDateTime.of(2024, 9, 25, 0, 0),
                close = BigDecimal("2.50"),
                open = BigDecimal("2.45"),
                high = BigDecimal("2.55"),
                low = BigDecimal("2.40"),
                volume = 500000,
                dividend = dividendRate,
                splitFactor = splitFactor,
                symbol = gne.code,
                exchange = "XNZE"
            )

        val response = MarketStackResponse(data = listOf(gneData))

        `when`(providerArguments.getAssets(batchId)).thenReturn(listOf(gne.code))
        `when`(providerArguments.getAsset(gne.code)).thenReturn(gne)
        `when`(providerArguments.getBatchConfigs(batchId)).thenReturn(DatedBatch(batchId, "today"))

        val result = marketStackAdapter.toMarketData(providerArguments, batchId, response)

        assertThat(result).hasSize(1)
        val marketData = result.first()
        assertThat(marketData.dividend).isEqualByComparingTo(dividendRate)
        assertThat(marketData.split).isEqualByComparingTo(splitFactor)
        assertThat(isDividend(marketData)).isTrue()
        assertThat(isSplit(marketData)).isTrue()
    }

    @Test
    fun `zero dividend and default split are preserved`() {
        val providerArguments = Mockito.mock(ProviderArguments::class.java)
        val batchId = 1
        val asset = Asset("FPH", market = NZX)

        val data =
            MarketStackData(
                date = LocalDateTime.of(2024, 11, 15, 0, 0),
                close = BigDecimal("34.50"),
                open = BigDecimal("34.20"),
                high = BigDecimal("34.75"),
                low = BigDecimal("34.10"),
                volume = 1254321,
                symbol = asset.code,
                exchange = "XNZE"
            )

        val response = MarketStackResponse(data = listOf(data))

        `when`(providerArguments.getAssets(batchId)).thenReturn(listOf(asset.code))
        `when`(providerArguments.getAsset(asset.code)).thenReturn(asset)
        `when`(providerArguments.getBatchConfigs(batchId)).thenReturn(DatedBatch(batchId, "today"))

        val result = marketStackAdapter.toMarketData(providerArguments, batchId, response)

        assertThat(result).hasSize(1)
        val marketData = result.first()
        assertThat(isDividend(marketData)).isFalse()
        assertThat(isSplit(marketData)).isFalse()
    }

    private fun createMarketStackData(symbol: String): MarketStackData =
        MarketStackData(
            date = LocalDateTime.now(),
            close = BigDecimal("150.00"),
            open = BigDecimal("145.00"),
            high = BigDecimal("155.00"),
            low = BigDecimal("140.00"),
            volume = 1,
            symbol = symbol,
            exchange = NASDAQ.code
        )
}