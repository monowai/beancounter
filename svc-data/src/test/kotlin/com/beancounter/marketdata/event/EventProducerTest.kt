package com.beancounter.marketdata.event

import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.USD
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.cloud.stream.function.StreamBridge
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Test suite for EventProducer to ensure proper corporate event publishing.
 *
 * This class tests:
 * - Corporate event publishing for dividend data
 * - Corporate event publishing for split data
 * - Event filtering for invalid or null market data
 * - Stream configuration and binding handling
 * - Event producer lifecycle and configuration
 *
 * Tests verify that the EventProducer correctly identifies and publishes
 * corporate events via Spring Cloud Stream when valid market data is provided.
 */
class EventProducerTest {
    private lateinit var eventProducer: EventProducer
    private lateinit var streamBridge: StreamBridge
    private val dateUtils = DateUtils()
    private lateinit var testAsset: Asset
    private lateinit var testMarketData: MarketData

    @BeforeEach
    fun setUp() {
        streamBridge = Mockito.mock(StreamBridge::class.java)
        eventProducer = EventProducer(streamBridge)
        eventProducer.streamEnabled = true

        testAsset =
            Asset(
                code = "AAPL",
                id = "AAPL",
                name = "Apple Inc.",
                market = Market("NASDAQ", USD.code),
                priceSymbol = "AAPL"
            )

        testMarketData =
            MarketData(
                asset = testAsset,
                source = "TEST",
                priceDate = LocalDate.now(),
                close = BigDecimal("150.00"),
                dividend = BigDecimal("0.50"),
                split = BigDecimal.ONE
            )
    }

    @Test
    fun `should publish dividend event when valid dividend market data is provided`() {
        // Given valid dividend market data
        val dividendMarketData =
            MarketData(
                asset = testAsset,
                source = "TEST",
                priceDate = LocalDate.now(),
                close = BigDecimal("150.00"),
                dividend = BigDecimal("0.50"),
                split = BigDecimal.ONE
            )

        // When the event producer writes the market data
        eventProducer.write(dividendMarketData)

        // Then the event should be published via StreamBridge
        verify(streamBridge).send(
            Mockito.eq("corporateEvent-out-0"),
            Mockito.any(TrustedEventInput::class.java)
        )
    }

    @Test
    fun `should publish split event when valid split market data is provided`() {
        // Given valid split market data
        val splitMarketData =
            MarketData(
                asset = testAsset,
                source = "TEST",
                priceDate = LocalDate.now(),
                close = BigDecimal("150.00"),
                dividend = BigDecimal.ZERO,
                split = BigDecimal("2.0")
            )

        // When the event producer writes the market data
        eventProducer.write(splitMarketData)

        // Then the event should be published via StreamBridge
        verify(streamBridge).send(
            Mockito.eq("corporateEvent-out-0"),
            Mockito.any(TrustedEventInput::class.java)
        )
    }

    @Test
    fun `should not publish event when market data has no dividend or split`() {
        // Given market data with no dividend or split
        val regularMarketData =
            MarketData(
                asset = testAsset,
                source = "TEST",
                priceDate = LocalDate.now(),
                close = BigDecimal("150.00"),
                dividend = BigDecimal.ZERO,
                split = BigDecimal.ONE
            )

        // When the event producer writes the market data
        eventProducer.write(regularMarketData)

        // Then no event should be published
        verifyNoInteractions(streamBridge)
    }

    @Test
    fun `should create correct corporate event structure for dividend`() {
        // Given dividend market data
        val dividendMarketData =
            MarketData(
                asset = testAsset,
                source = "TEST",
                priceDate = LocalDate.now(),
                close = BigDecimal("150.00"),
                dividend = BigDecimal("0.75"),
                split = BigDecimal.ONE
            )

        // When the event producer writes the market data
        eventProducer.write(dividendMarketData)

        // Then the correct corporate event should be created and sent
        verify(streamBridge).send(
            Mockito.eq("corporateEvent-out-0"),
            Mockito.argThat { eventInput: TrustedEventInput ->
                val event = eventInput.data
                event.trnType == TrnType.DIVI &&
                    event.assetId == testAsset.id &&
                    event.rate == BigDecimal("0.75") &&
                    event.split == BigDecimal.ONE &&
                    event.source == "TEST" &&
                    event.recordDate == LocalDate.now()
            }
        )
    }

    @Test
    fun `should create correct corporate event structure for split`() {
        // Given split market data
        val splitMarketData =
            MarketData(
                asset = testAsset,
                source = "TEST",
                priceDate = LocalDate.now(),
                close = BigDecimal("150.00"),
                dividend = BigDecimal.ZERO,
                split = BigDecimal("3.0")
            )

        // When the event producer writes the market data
        eventProducer.write(splitMarketData)

        // Then the correct corporate event should be created and sent
        verify(streamBridge).send(
            Mockito.eq("corporateEvent-out-0"),
            Mockito.argThat { eventInput: TrustedEventInput ->
                val event = eventInput.data
                event.trnType == TrnType.DIVI &&
                    event.assetId == testAsset.id &&
                    event.rate == BigDecimal.ZERO &&
                    event.split == BigDecimal("3.0") &&
                    event.source == "TEST" &&
                    event.recordDate == LocalDate.now(dateUtils.zoneId)
            }
        )
    }

    @Test
    fun `should handle both dividend and split in same market data`() {
        // Given market data with both dividend and split
        val combinedMarketData =
            MarketData(
                asset = testAsset,
                source = "TEST",
                priceDate = LocalDate.now(),
                close = BigDecimal("150.00"),
                dividend = BigDecimal("0.25"),
                split = BigDecimal("2.0")
            )

        // When the event producer writes the market data
        eventProducer.write(combinedMarketData)

        // Then the event should be published with both values
        verify(streamBridge).send(
            Mockito.eq("corporateEvent-out-0"),
            Mockito.argThat { eventInput: TrustedEventInput ->
                val event = eventInput.data
                event.rate == BigDecimal("0.25") &&
                    event.split == BigDecimal("2.0")
            }
        )
    }
}