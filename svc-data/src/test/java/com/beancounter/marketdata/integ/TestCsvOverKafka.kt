package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenService
import com.beancounter.auth.common.TokenUtils
import com.beancounter.client.AssetService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.ImportFormat.SHARESIGHT
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType.BUY
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.event.EventWriter
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.providers.PriceWriter
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.service.MarketDataService
import com.beancounter.marketdata.trn.TrnImport
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.RegistrationUtils
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File
import java.math.BigDecimal
import java.util.Objects

@EmbeddedKafka(
    partitions = 1,
    topics = [
        TestCsvOverKafka.TOPIC_TRN_CSV,
        "topicPrice",
        TestCsvOverKafka.TOPIC_EVENT,
        TestCsvOverKafka.TOPIC_CSV_IO
    ],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = ["log.dir=./build/kafka", "auto.create.topics.enable=true"]
)
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("kafka")
@Tag("slow")
class TestCsvOverKafka {

    private val objectMapper = BcJson().objectMapper

    @Autowired
    lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    private lateinit var token: Jwt

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
    lateinit var systemUserService: SystemUserService

    @MockBean
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var trnImport: TrnImport

    @Autowired
    lateinit var trnService: TrnService

    @Autowired
    lateinit var eventWriter: EventWriter

    @Autowired
    lateinit var currencyService: CurrencyService

    val consumers: MutableMap<String, Consumer<String, String>> = HashMap()

    private fun getKafkaConsumer(group: String, topic: String): Consumer<String, String> {
        var consumer = consumers[group]
        if (consumer == null) {
            val consumerProps = KafkaTestUtils.consumerProps(group, "false", embeddedKafkaBroker)
            consumerProps["session.timeout.ms"] = 6000
            consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            val cf = DefaultKafkaConsumerFactory<String, String>(consumerProps)
            consumer = cf.createConsumer()
            consumers[group] = consumer
            embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, topic)
        }
        return consumer!!
    }

    @Test
    fun is_StaticDataLoaded() {
        assertThat(currencyService.currencies).isNotEmpty
        assertThat(eventWriter.kafkaEnabled).isTrue
    }

    @Test
    fun is_ExportFileImported() {
        // Setup
        val (id) = systemUserService.save(SystemUser("mike"))
        Mockito.`when`(tokenService.subject).thenReturn(id)

        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
        val user = SystemUser("TrnMvcTest", "user@testing.com")
        token = TokenUtils().getUserToken(user)
        RegistrationUtils.registerUser(mockMvc, token)

        val qcomRequest = AssetRequest("QCOM", getAssetInput("NASDAQ", "QCOM"))
        val qcomResponse = assetService.process(qcomRequest)!!
        assertThat(qcomResponse).isNotNull
        val qcom = qcomResponse.data.iterator().next().value

        val trexRequest = AssetRequest("TREX", getAssetInput("NASDAQ", "TREX"))
        val trexResponse = assetService.process(trexRequest)!!
        val trex = trexResponse.data.iterator().next().value
        assertThat(trexResponse).isNotNull

        val portfolios: MutableCollection<PortfolioInput> = ArrayList()
        portfolios.add(PortfolioInput("CSV-FLOW", "CSV-FLOW", "USD"))
        val pfResponse = portfolioService.save(portfolios)
        assertThat(pfResponse).isNotNull.hasSize(1)
        val portfolio = pfResponse.iterator().next()
        val aaaInput = TrnInput(
            CallerRef("BC", "batch", "1"),
            qcom.id,
            BUY,
            quantity = BigDecimal.TEN,
            fees = BigDecimal.ONE,
            price = BigDecimal(5.99)
        )
        val bbbInput = TrnInput(
            CallerRef("BC", "batch", "2"),
            trex.id,
            BUY,
            quantity = BigDecimal.TEN,
            fees = BigDecimal.ONE,
            price = BigDecimal(5.99)
        )

        val trnResponse = trnService.save(portfolio, TrnRequest(portfolio.id, arrayOf(aaaInput, bbbInput)))
        val expectedSize = 2
        assertThat(trnResponse.data).isNotNull.hasSize(expectedSize)
        // End Setup

        val results = mockMvc.perform(
            MockMvcRequestBuilders.get("/trns/portfolio/{portfolioId}/export", portfolio.id)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token)
                        .authorities(RegistrationUtils.authorityRoleConverter)
                )
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN_VALUE))
            .andReturn()
        assertThat(results).isNotNull
        val fileName = "csvExport.txt"
        File(fileName).bufferedWriter().use { out ->
            out.write(results.response.contentAsString)
        }

        trnService.purge(portfolio)
        assertThat(trnService.findForPortfolio(portfolio, dateUtils.date).data).isEmpty()
        val consumer = getKafkaConsumer("is_ExportFileImported", TOPIC_CSV_IO)

        val csvRows = readFileAsLinesUsingUseLines(fileName)
        assertThat(csvRows).hasSize(3)
        var currentLine = 1
        for (csvRow in csvRows) {
            if (currentLine++ == 1) {
                // Header
                assertThat(csvRow.startsWith("portfolio"))
            } else {
                // Trn
                val splitRow = csvRow.split(",")
                val bcRequest = TrustedTrnImportRequest(portfolio, splitRow)
                log.info("Sending {}, {}, {}", splitRow[0], splitRow[1], splitRow[2])
                kafkaWriter.send(TOPIC_CSV_IO, bcRequest).get()
            }
        }
        assertThat(trnQueueResponse(consumer))
            .isNotNull
            .hasNoNullFieldsOrProperties()
        consumer.close()
        val imported = trnService.findForPortfolio(portfolio, dateUtils.date).data
        assertThat(imported).hasSize(expectedSize)
    }

    fun readFileAsLinesUsingUseLines(fileName: String): List<String> = File(fileName).useLines { it.toList() }

    @Test
    @Throws(Exception::class)
    fun is_TrnRequestSentAndReceived() {
        assertThat(currencyService.currencies).isNotEmpty
        val (id) = systemUserService.save(SystemUser("mike"))
        Mockito.`when`(tokenService.subject).thenReturn(id)

        // The asset has to exist
        val assetRequest = AssetRequest("MSFT", getAssetInput("NASDAQ", "MSFT"))
        val assetResponse = assetService.process(assetRequest)!!
        assertThat(assetResponse.data["MSFT"]).hasFieldOrProperty("id")
        val portfolios: MutableCollection<PortfolioInput> = ArrayList()
        portfolios.add(PortfolioInput("SS-TEST", "SS-TEST", "USD"))
        val pfResponse = portfolioService.save(portfolios)
        assertThat(pfResponse).isNotNull
        assertThat(pfResponse).isNotNull.hasSize(1)

        // A CSV row
        val row: List<String> = listOf(
            "123",
            "NASDAQ",
            "MSFT",
            "Test Asset",
            "BUY",
            "21/01/2019",
            BigDecimal.TEN.toString(),
            BigDecimal.ONE.toString(),
            BigDecimal.ZERO.toString(),
            "USD",
            BigDecimal.ONE.toString(),
            BigDecimal.TEN.toString(),
            "Test Comment"
        )
        val csvShareSightRequest = TrustedTrnImportRequest(pfResponse.iterator().next(), row, SHARESIGHT)
        val consumer = getKafkaConsumer("is_TrnRequestSentAndReceived", TOPIC_TRN_CSV)
        kafkaWriter.send(TOPIC_TRN_CSV, csvShareSightRequest)
        val trnResponse = trnTopicResponse(consumer, TOPIC_TRN_CSV)
        val expectedAsset = assetResponse.data["MSFT"]
        for (trn in trnResponse.data) {
            assertThat(trn.asset).usingRecursiveComparison().isEqualTo(expectedAsset)
            assertThat(trn.callerRef).hasFieldOrPropertyWithValue("callerId", "123")
        }
        consumer.close()
    }

    @Test
    fun is_MutualFundProcessed() {
        log.debug(embeddedKafkaBroker.brokersAsString)
        val (id) = systemUserService.save(SystemUser("mike"))
        Mockito.`when`(tokenService.subject).thenReturn(id)

        // The asset has to exist
        val assetRequest = AssetRequest("B784NS1", getAssetInput("LON", "B784NS1"))
        val assetResponse = assetService.process(assetRequest)!!
        assertThat(assetResponse.data["B784NS1"]).hasFieldOrProperty("id")
        val portfolios: MutableCollection<PortfolioInput> = ArrayList()
        portfolios.add(PortfolioInput("SS-MFTEST", "SS-MFTST", "USD"))
        val pfResponse = portfolioService.save(portfolios)
        assertThat(pfResponse).isNotNull
        assertThat(pfResponse).isNotNull.hasSize(1)

        // A CSV row
        val row: List<String> = listOf(
            "10",
            "LON",
            "B784NS1",
            "",
            "BUY",
            "01/01/2020",
            "3245.936",
            "2.4646",
            "0",
            "GBP",
            BigDecimal.ONE.toString(),
            "",
            "Test Comment"
        )
        val trnRequest = TrustedTrnImportRequest(
            pfResponse.iterator().next(), row, SHARESIGHT
        )
        val expectedAsset = assetResponse.data["B784NS1"]
        val response = trnImport.fromCsvImport(trnRequest)
        assertThat(response).isNotNull
        assertThat(response.data).isNotNull.hasSize(1)
        for (trn in response.data) {
            assertThat(trn.asset).usingRecursiveComparison().isEqualTo(expectedAsset)
            assertThat(trn.callerRef).hasFieldOrPropertyWithValue("callerId", "10")
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_PricePersisted() {
        val assetRequest = AssetRequest("test", getAssetInput("NASDAQ", "MSFT"))
        val assetResult = assetService.process(assetRequest)!!
        val asset = assetResult.data["test"]
        assertThat(asset).isNotNull.hasFieldOrProperty("id")
        val priceDate = "2020-04-29"
        val marketData = MarketData(
            asset!!,
            Objects.requireNonNull(dateUtils.getDate(priceDate, dateUtils.getZoneId()))
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
            assertThat(result).hasFieldOrProperty("id")
            val assetInput = AssetInput(asset.market.code, asset.code, asset)
            assets.add(assetInput)
        }

        // Will be resolved over the mocked API
        assets.add(
            AssetInput(
                "NASDAQ", "APPL",
                getAsset("NASDAQ", "AAPL")
            )
        )
        val priceRequest = PriceRequest(priceDate, assets)

        // First call will persist the result in an async manner
        priceResponse = marketDataService.getPriceResponse(priceRequest)
        assertThat(priceResponse).isNotNull
        assertThat(priceResponse.data).isNotEmpty.hasSize(2)
        Thread.sleep(2000)
        // Second call will retrieve from DB to assert objects are correctly hydrated
        priceResponse = marketDataService.getPriceResponse(priceRequest)
        assertThat(priceResponse).isNotNull
        assertThat(priceResponse.data).isNotNull.isNotEmpty.hasSize(2)
        for ((asset1) in priceResponse.data) {
            assertThat(asset1).isNotNull
                .hasFieldOrProperty("id")
            assertThat(asset1.market) // These are not used client side so should be ignored
                .hasNoNullFieldsOrPropertiesExcept("currencyId", "timezoneId", "enricher")
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_CorporateEventDispatched() {
        val data: MutableMap<String, AssetInput> = HashMap()
        data["a"] = AssetInput("NASDAQ", "TWEE")
        val assetResult = assetService.process(AssetRequest(data))!!
        assertThat(assetResult.data).hasSize(1)
        val asset = assetResult.data["a"]
        assertThat(asset!!.id).isNotNull
        assertThat(asset.market).isNotNull
        val marketData = MarketData(
            asset,
            Objects.requireNonNull(dateUtils.getDate("2019-12-10", dateUtils.getZoneId()))
        )
        marketData.source = "ALPHA"
        marketData.dividend = BigDecimal("2.34")
        marketData.split = BigDecimal("1.000")
        val consumer = getKafkaConsumer("is_CorporateEventDispatched", TOPIC_EVENT)

        // Compare with a serialised event
        eventWriter.write(marketData)
        val consumerRecord = KafkaTestUtils.getSingleRecord(consumer, TOPIC_EVENT)
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

    private fun trnQueueResponse(consumer: Consumer<String, String>): TrnResponse {
        val consumerRecords = KafkaTestUtils.getRecords(consumer, 100, 2)
        val created = ArrayList<Trn>()
        for (consumerRecord in consumerRecords) {
            assertThat(consumerRecord.value()).isNotNull
            val received = objectMapper
                .readValue(consumerRecord.value(), TrustedTrnImportRequest::class.java)
            val trnResponse = trnImport.fromCsvImport(received)
            assertThat(trnResponse).isNotNull
            assertThat(trnResponse.data)
                .isNotNull
                .hasSize(1)
            val found = trnResponse.data.iterator().next()
            log.info("found {}", found.callerRef)
            created.add(found)
        }
        return TrnResponse(created)
    }

    private fun trnTopicResponse(consumer: Consumer<String, String>, topic: String): TrnResponse {
        val consumerRecord = KafkaTestUtils.getSingleRecord(consumer, topic)
        assertThat(consumerRecord.value()).isNotNull
        val received = objectMapper
            .readValue(consumerRecord.value(), TrustedTrnImportRequest::class.java)
        val trnResponse = trnImport.fromCsvImport(received)
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse.data)
            .isNotNull
            .hasSize(1)
        return trnResponse
    }

    companion object {
        const val TOPIC_TRN_CSV = "topicTrnCsv"
        const val TOPIC_CSV_IO = "topicCsvIo"
        const val TOPIC_EVENT = "topicEvent"
        private val log = LoggerFactory.getLogger(TestCsvOverKafka::class.java)
    }
}
