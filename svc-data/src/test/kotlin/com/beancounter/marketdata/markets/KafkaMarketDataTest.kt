package com.beancounter.marketdata.markets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.KafkaBase
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.event.EventWriter
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.PriceWriter
import com.beancounter.marketdata.utils.KafkaConsumerUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

/**
 * Non-trn related integrations over Kafka. Price and Corporate Action Events.
 */

class KafkaMarketDataTest : KafkaBase() {

    companion object {
        const val TOPIC_EVENT = "topicEvent"
    }

    private val objectMapper = BcJson().objectMapper

    final var dateUtils: DateUtils = DateUtils()

    @Autowired
    private lateinit var wac: WebApplicationContext

    @Autowired
    lateinit var marketDataService: MarketDataService

    @MockBean
    lateinit var assetService: AssetService

    @Autowired
    lateinit var priceWriter: PriceWriter

    @Autowired
    lateinit var portfolioService: PortfolioService

    @Autowired
    lateinit var eventWriter: EventWriter

    @Autowired
    lateinit var currencyService: CurrencyService

    private val kafkaTestUtils = KafkaConsumerUtils()

    @Test
    fun corporateEventDispatched() {
        val data: MutableMap<String, AssetInput> = HashMap()
        data["a"] = AssetInput(NASDAQ.code, "TWEE")
        val assetRequest = AssetRequest(data)
        `when`(assetService.handle(assetRequest))
            .thenReturn(AssetUpdateResponse(mapOf(Pair("a", getTestAsset(code = "id", market = NASDAQ)))))
        val assetResult = assetService.handle(assetRequest)
        assertThat(assetResult.data).hasSize(1)
        val asset = assetResult.data["a"]
        assertThat(asset!!.id).isNotNull
        assertThat(asset.market).isNotNull
        val marketData = MarketData(
            asset,
            dateUtils.getDate("2019-12-10", dateUtils.getZoneId()),
        )
        marketData.source = "ALPHA"
        marketData.dividend = BigDecimal("2.34")
        marketData.split = BigDecimal("1.000")
        val consumer = kafkaTestUtils.getConsumer(
            "is_CorporateEventDispatched",
            TOPIC_EVENT,
            embeddedKafkaBroker,
        )

        // Compare with a serialised event
        eventWriter.write(marketData)
        val consumerRecord = KafkaTestUtils.getSingleRecord(consumer, TOPIC_EVENT)
        assertThat(consumerRecord.value()).isNotNull
        val (data1) = objectMapper.readValue(
            consumerRecord.value(),
            TrustedEventInput::class.java,
        )
        assertThat(data1)
            .hasFieldOrPropertyWithValue("rate", marketData.dividend)
            .hasFieldOrPropertyWithValue("assetId", asset.id)
            .hasFieldOrProperty("recordDate")
    }
}
