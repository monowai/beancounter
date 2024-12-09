package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.providers.DatedBatch
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.marketstack.model.MarketStackData
import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
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