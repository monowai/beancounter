package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.marketdata.Constants.Companion.US
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Test the AlphaPriceDeserializer to verify change and changePercent calculation
 * and TIME_SERIES_DAILY_ADJUSTED parsing with split/dividend data.
 */
class AlphaPriceDeserializerTest {
    private val deserializer = AlphaPriceDeserializer()
    private val asset = getTestAsset(US, "DPZ")
    private val objectMapper = ObjectMapper()

    @Test
    fun `should correctly deserialize change and changePercent from API response`() {
        // Given: API response data with change and previousClose
        val apiResponse =
            """
            {
                "Global Quote": {
                    "01. symbol": "DPZ",
                    "02. open": "450.0000",
                    "03. high": "460.0000",
                    "04. low": "445.0000",
                    "05. price": "456.6200",
                    "06. volume": "1000000",
                    "07. latest trading day": "2025-09-11",
                    "08. previous close": "450.5600",
                    "09. change": "6.0600",
                    "10. change percent": "1.3450%"
                }
            }
            """.trimIndent()

        // When: Deserializing the response
        objectMapper.readTree(apiResponse)
        val parser = objectMapper.createParser(apiResponse)
        val context = mock(DeserializationContext::class.java)
        val priceResponse = deserializer.deserialize(parser, context)

        // Then: The MarketData should have correct change and changePercent values
        assertEquals(1, priceResponse.data.size)
        val marketData = priceResponse.data.first()

        assertEquals(asset.code, marketData.asset.code)
        assertEquals(asset.market, marketData.asset.market)
        assertEquals(BigDecimal("456.6200"), marketData.close)
        assertEquals(BigDecimal("450.0000"), marketData.open)
        assertEquals(BigDecimal("445.0000"), marketData.low)
        assertEquals(BigDecimal("460.0000"), marketData.high)
        assertEquals(BigDecimal("450.5600"), marketData.previousClose)
        assertEquals(BigDecimal("6.0600"), marketData.change)
        assertEquals(BigDecimal("0.013450"), marketData.changePercent) // 1.3450% as decimal
        assertEquals(1000000, marketData.volume)
        assertEquals("ALPHA", marketData.source)
    }

    @Test
    fun `should handle zero change correctly`() {
        // Given: API response with zero change
        val apiResponse =
            """
            {
                "Global Quote": {
                    "01. symbol": "DPZ",
                    "02. open": "450.0000",
                    "03. high": "450.0000",
                    "04. low": "450.0000",
                    "05. price": "450.0000",
                    "06. volume": "1000000",
                    "07. latest trading day": "2025-09-11",
                    "08. previous close": "450.0000",
                    "09. change": "0.0000",
                    "10. change percent": "0.0000%"
                }
            }
            """.trimIndent()

        // When: Deserializing the response
        val parser = objectMapper.createParser(apiResponse)
        val context = mock(DeserializationContext::class.java)
        val priceResponse = deserializer.deserialize(parser, context)

        // Then: The MarketData should have zero change and changePercent
        assertEquals(1, priceResponse.data.size)
        val marketData = priceResponse.data.first()

        assertEquals(BigDecimal("450.0000"), marketData.close)
        assertEquals(BigDecimal("450.0000"), marketData.open)
        assertEquals(BigDecimal("450.0000"), marketData.low)
        assertEquals(BigDecimal("450.0000"), marketData.high)
        assertEquals(BigDecimal("450.0000"), marketData.previousClose)
        assertEquals(BigDecimal("0.0000"), marketData.change)
        assertEquals(BigDecimal.ZERO, marketData.changePercent)
        assertEquals(1000000, marketData.volume)
        assertEquals("ALPHA", marketData.source)
    }

    @Test
    fun `should handle negative change correctly`() {
        // Given: API response with negative change
        val apiResponse =
            """
            {
                "Global Quote": {
                    "01. symbol": "DPZ",
                    "02. open": "450.0000",
                    "03. high": "450.0000",
                    "04. low": "440.0000",
                    "05. price": "445.0000",
                    "06. volume": "1000000",
                    "07. latest trading day": "2025-09-11",
                    "08. previous close": "450.0000",
                    "09. change": "-5.0000",
                    "10. change percent": "-1.1111%"
                }
            }
            """.trimIndent()

        // When: Deserializing the response
        val parser = objectMapper.createParser(apiResponse)
        val context = mock(DeserializationContext::class.java)
        val priceResponse = deserializer.deserialize(parser, context)

        // Then: The MarketData should have negative change and changePercent
        assertEquals(1, priceResponse.data.size)
        val marketData = priceResponse.data.first()

        assertEquals(BigDecimal("445.0000"), marketData.close)
        assertEquals(BigDecimal("450.0000"), marketData.open)
        assertEquals(BigDecimal("440.0000"), marketData.low)
        assertEquals(BigDecimal("450.0000"), marketData.high)
        assertEquals(BigDecimal("450.0000"), marketData.previousClose)
        assertEquals(BigDecimal("-5.0000"), marketData.change)
        assertEquals(BigDecimal("-0.011111"), marketData.changePercent) // -1.1111% as decimal
        assertEquals(1000000, marketData.volume)
        assertEquals("ALPHA", marketData.source)
    }

    @Test
    fun `should correctly parse TIME_SERIES_DAILY_ADJUSTED with 2-1 split`() {
        // Given: TIME_SERIES_DAILY_ADJUSTED response with a 2:1 split on 2025-12-05
        val apiResponse =
            """
            {
                "Meta Data": {
                    "1. Information": "Daily Time Series with Splits and Dividend Events",
                    "2. Symbol": "XLK",
                    "3. Last Refreshed": "2025-12-05",
                    "4. Output Size": "Compact",
                    "5. Time Zone": "US/Eastern"
                },
                "Time Series (Daily)": {
                    "2025-12-05": {
                        "1. open": "145.0000",
                        "2. high": "148.0000",
                        "3. low": "144.0000",
                        "4. close": "146.6000",
                        "5. adjusted close": "146.6000",
                        "6. volume": "5000000",
                        "7. dividend amount": "0.0000",
                        "8. split coefficient": "2.0"
                    },
                    "2025-12-04": {
                        "1. open": "290.0000",
                        "2. high": "292.0000",
                        "3. low": "288.0000",
                        "4. close": "291.0700",
                        "5. adjusted close": "145.535",
                        "6. volume": "3000000",
                        "7. dividend amount": "0.0000",
                        "8. split coefficient": "1.0"
                    }
                }
            }
            """.trimIndent()

        // When: Deserializing the response
        val parser = objectMapper.createParser(apiResponse)
        val context = mock(DeserializationContext::class.java)
        val priceResponse = deserializer.deserialize(parser, context)

        // Then: Should parse both dates
        assertEquals(2, priceResponse.data.size)

        // And: The split date should have split coefficient = 2.0
        val splitDayData = priceResponse.data.find { it.priceDate.isEqual(LocalDate.of(2025, 12, 5)) }
        requireNotNull(splitDayData) { "Expected to find data for 2025-12-05" }
        assertEquals(0, BigDecimal("2.0").compareTo(splitDayData.split))
        assertEquals(0, BigDecimal("146.6000").compareTo(splitDayData.close))
        assertEquals(0, BigDecimal.ZERO.compareTo(splitDayData.dividend))

        // And: The day before should have split coefficient = 1.0
        val preDayData = priceResponse.data.find { it.priceDate.isEqual(LocalDate.of(2025, 12, 4)) }
        requireNotNull(preDayData) { "Expected to find data for 2025-12-04" }
        assertEquals(0, BigDecimal("1.0").compareTo(preDayData.split))
        assertEquals(0, BigDecimal("291.0700").compareTo(preDayData.close))
    }

    @Test
    fun `should correctly parse TIME_SERIES_DAILY_ADJUSTED with dividend`() {
        // Given: TIME_SERIES_DAILY_ADJUSTED response with a dividend
        val apiResponse =
            """
            {
                "Meta Data": {
                    "1. Information": "Daily Time Series with Splits and Dividend Events",
                    "2. Symbol": "AAPL",
                    "3. Last Refreshed": "2025-11-15",
                    "4. Output Size": "Compact",
                    "5. Time Zone": "US/Eastern"
                },
                "Time Series (Daily)": {
                    "2025-11-15": {
                        "1. open": "180.0000",
                        "2. high": "182.0000",
                        "3. low": "179.0000",
                        "4. close": "181.0000",
                        "5. adjusted close": "180.75",
                        "6. volume": "4000000",
                        "7. dividend amount": "0.25",
                        "8. split coefficient": "1.0"
                    }
                }
            }
            """.trimIndent()

        // When: Deserializing the response
        val parser = objectMapper.createParser(apiResponse)
        val context = mock(DeserializationContext::class.java)
        val priceResponse = deserializer.deserialize(parser, context)

        // Then: Should parse the dividend correctly
        assertEquals(1, priceResponse.data.size)
        val marketData = priceResponse.data.first()

        assertEquals(0, BigDecimal("0.25").compareTo(marketData.dividend))
        assertEquals(0, BigDecimal("1.0").compareTo(marketData.split))
        assertEquals(0, BigDecimal("181.0000").compareTo(marketData.close))
    }

    @Test
    fun `isSplit should return true for non-1 split coefficient`() {
        // Given: MarketData with split coefficient != 1
        val marketDataWithSplit =
            MarketData(
                asset = asset,
                priceDate = LocalDate.of(2025, 12, 5),
                close = BigDecimal("100.00"),
                split = BigDecimal("2.0")
            )

        // When/Then: isSplit should return true
        assertEquals(true, MarketData.isSplit(marketDataWithSplit))
    }

    @Test
    fun `isSplit should return false for split coefficient of 1`() {
        // Given: MarketData with split coefficient = 1
        val marketDataNoSplit =
            MarketData(
                asset = asset,
                priceDate = LocalDate.of(2025, 12, 5),
                close = BigDecimal("100.00"),
                split = BigDecimal.ONE
            )

        // When/Then: isSplit should return false
        assertEquals(false, MarketData.isSplit(marketDataNoSplit))
    }

    @Test
    fun `isDividend should return true for non-zero dividend`() {
        // Given: MarketData with dividend > 0
        val marketDataWithDividend =
            MarketData(
                asset = asset,
                priceDate = LocalDate.of(2025, 12, 5),
                close = BigDecimal("100.00"),
                dividend = BigDecimal("0.25")
            )

        // When/Then: isDividend should return true
        assertEquals(true, MarketData.isDividend(marketDataWithDividend))
    }

    @Test
    fun `isDividend should return false for zero dividend`() {
        // Given: MarketData with dividend = 0
        val marketDataNoDividend =
            MarketData(
                asset = asset,
                priceDate = LocalDate.of(2025, 12, 5),
                close = BigDecimal("100.00"),
                dividend = BigDecimal.ZERO
            )

        // When/Then: isDividend should return false
        assertEquals(false, MarketData.isDividend(marketDataNoDividend))
    }
}