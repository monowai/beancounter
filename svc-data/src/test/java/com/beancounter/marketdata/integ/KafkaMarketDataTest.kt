package com.beancounter.marketdata.integ

import com.beancounter.client.AssetService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.event.EventWriter
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.providers.PriceWriter
import com.beancounter.marketdata.service.MarketDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

/**
 * Non-trn related integrations over Kafka. Price and Corporate Action Events.
 */
@EmbeddedKafka(
    partitions = 1,
    topics = [
        "topicPrice",
        KafkaTrnTest.TOPIC_EVENT,
    ],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = ["log.dir=./build/kafka-md", "auto.create.topics.enable=true"]
)
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("kafka")
@Tag("slow")
class KafkaMarketDataTest {
    private val objectMapper = BcJson().objectMapper

    final var dateUtils: DateUtils = DateUtils()

    @Autowired
    private lateinit var wac: WebApplicationContext

    // Setup so that the wiring is tested
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    lateinit var kafkaWriter: KafkaTemplate<Any, TrustedTrnImportRequest>

    @Autowired
    lateinit var marketDataService: MarketDataService

    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var priceWriter: PriceWriter

    @Autowired
    lateinit var portfolioService: PortfolioService

    @Autowired
    lateinit var eventWriter: EventWriter

    @Autowired
    lateinit var currencyService: CurrencyService

    private final val kafkaTestUtils = KafkaConsumerUtils()

    @Test
    @Throws(Exception::class)
    fun pricePersisted() {
        val assetRequest = AssetRequest("test", AssetUtils.getAssetInput(NASDAQ.code, MSFT.code))
        val assetResult = assetService.process(assetRequest)!!
        val asset = assetResult.data["test"]
        val idProp = "id"
        assertThat(asset).isNotNull.hasFieldOrProperty(idProp)
        val priceDate = "2020-04-29"
        val marketData = MarketData(
            asset!!,
            dateUtils.getDate(priceDate, dateUtils.getZoneId())
        )
        marketData.volume = 10
        marketData.open = BigDecimal.TEN
        marketData.dividend = BigDecimal.ZERO
        val mdCollection: MutableCollection<MarketData> = ArrayList()
        mdCollection.add(marketData)
        var priceResponse = PriceResponse(mdCollection)
        val assets: MutableCollection<AssetInput> = ArrayList()
        val results = priceWriter.processMessage(
            objectMapper.writeValueAsString(priceResponse)
        )
        assertThat(results).isNotNull.isNotEmpty
        for (result in results!!) {
            assertThat(result).hasFieldOrProperty(idProp)
            val assetInput = AssetInput(asset.market.code, asset.code, asset)
            assets.add(assetInput)
        }

        // Will be resolved over the mocked API
        assets.add(
            AssetInput(
                NASDAQ.code, Constants.AAPL.code,
                AssetUtils.getAsset(NASDAQ.code, Constants.AAPL.code)
            )
        )
        val priceRequest = PriceRequest(priceDate, assets)

        // First call will persist the result in an async manner
        priceResponse = marketDataService.getPriceResponse(priceRequest)
        assertThat(priceResponse).isNotNull
        assertThat(priceResponse.data).hasSize(2)
        Thread.sleep(2000)
        // Second call will retrieve from DB to assert objects are correctly hydrated
        priceResponse = marketDataService.getPriceResponse(priceRequest)
        assertThat(priceResponse).isNotNull
        assertThat(priceResponse.data).hasSize(2)
        for ((asset1) in priceResponse.data) {
            assertThat(asset1).isNotNull
                .hasFieldOrProperty(idProp)
            assertThat(asset1.market) // These are not used client side so should be ignored
                .hasNoNullFieldsOrPropertiesExcept("currencyId", "timezoneId", "enricher")
        }
    }

    @Test
    @Throws(Exception::class)
    fun corporateEventDispatched() {
        val data: MutableMap<String, AssetInput> = HashMap()
        data["a"] = AssetInput(NASDAQ.code, "TWEE")
        val assetResult = assetService.process(AssetRequest(data))!!
        assertThat(assetResult.data).hasSize(1)
        val asset = assetResult.data["a"]
        assertThat(asset!!.id).isNotNull
        assertThat(asset.market).isNotNull
        val marketData = MarketData(
            asset,
            dateUtils.getDate("2019-12-10", dateUtils.getZoneId())
        )
        marketData.source = "ALPHA"
        marketData.dividend = BigDecimal("2.34")
        marketData.split = BigDecimal("1.000")
        val consumer = kafkaTestUtils.getConsumer(
            "is_CorporateEventDispatched",
            KafkaTrnTest.TOPIC_EVENT,
            embeddedKafkaBroker
        )

        // Compare with a serialised event
        eventWriter.write(marketData)
        val consumerRecord = KafkaTestUtils.getSingleRecord(consumer, KafkaTrnTest.TOPIC_EVENT)
        assertThat(consumerRecord.value()).isNotNull
        val (data1) = objectMapper.readValue(
            consumerRecord.value(),
            TrustedEventInput::class.java
        )
        assertThat(data1)
            .hasFieldOrPropertyWithValue("rate", marketData.dividend)
            .hasFieldOrPropertyWithValue("assetId", asset.id)
            .hasFieldOrProperty("recordDate")
    }
}
