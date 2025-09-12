package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.marketdata.Constants.Companion.US
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal

/**
 * Test the AlphaPriceDeserializer to verify change and changePercent calculation
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
}