package com.beancounter.marketdata.broker

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcKafkaTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.providers.MarketDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.math.BigDecimal

/**
 * Non-trn related integrations over Kafka. Price and Corporate Action Events.
 */

@SpringMvcKafkaTest
class KafkaMarketStackDataTest {
    /**
     * Constants to support integration tests.
     */
    companion object {
        const val TOPIC_EVENT = "topicEvent"
        const val TOPIC_MV = "topicMv"
    }

    // Setup so that the wiring is tested
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    lateinit var dateUtils: DateUtils

    @Autowired
    lateinit var marketDataService: MarketDataService

    @MockBean
    lateinit var assetService: AssetService

    @Autowired
    lateinit var eventProducer: EventProducer

    @Autowired
    lateinit var currencyService: CurrencyService

    @Test
    fun corporateEventDispatched() {
        val data: MutableMap<String, AssetInput> = mutableMapOf(Pair("a", AssetInput(NASDAQ.code, "TWEE")))
        val assetRequest = AssetRequest(data)
        `when`(assetService.handle(assetRequest))
            .thenReturn(AssetUpdateResponse(mapOf(Pair("a", getTestAsset(code = "id", market = NASDAQ)))))
        val assetResult = assetService.handle(assetRequest)
        assertThat(assetResult.data).hasSize(1).containsKeys("a")
        val asset = assetResult.data["a"]
        assertThat(asset!!.id).isNotNull
        assertThat(asset.market).isNotNull
        val marketData =
            MarketData(
                asset,
                dateUtils.getDate("2019-12-10"),
            )
        marketData.source = "ALPHA"
        marketData.dividend = BigDecimal("2.34")
        marketData.split = BigDecimal("1.000")
        // Compare with a serialised event
        eventProducer.write(marketData)

        val consumer =
            KafkaConsumerUtils().getConsumer(
                this::class.toString(),
                TOPIC_EVENT,
                embeddedKafkaBroker,
            )

        val consumerRecord = KafkaTestUtils.getSingleRecord(consumer, TOPIC_EVENT)
        consumer.close()
        assertThat(consumerRecord.value()).isNotNull
        val (eventInput) =
            objectMapper.readValue(
                consumerRecord.value(),
                TrustedEventInput::class.java,
            )
        assertThat(eventInput)
            .hasFieldOrPropertyWithValue("rate", marketData.dividend)
            .hasFieldOrPropertyWithValue("assetId", asset.id)
            .hasFieldOrProperty("recordDate")
    }
}
