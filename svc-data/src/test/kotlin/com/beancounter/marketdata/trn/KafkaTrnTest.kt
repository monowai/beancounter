package com.beancounter.marketdata.trn

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.client.AssetService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType.BUY
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.CUSTOM
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.event.EventWriter
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.PriceWriter
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.utils.KafkaConsumerUtils
import com.beancounter.marketdata.utils.RegistrationUtils
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import org.apache.kafka.clients.consumer.Consumer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.io.File
import java.math.BigDecimal
import java.math.BigDecimal.ONE

/**
 * CSV file export and import via Kafka.
 */
@EmbeddedKafka(
    partitions = 1,
    topics = [
        KafkaTrnTest.TOPIC_CSV_IO
    ],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    brokerProperties = ["log.dir=./build/kafka-trn", "auto.create.topics.enable=true"]
)
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("kafka")
@Tag("slow")
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockAuth
@AutoConfigureMockMvc
class KafkaTrnTest {

    final var dateUtils: DateUtils = DateUtils()

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

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

    @MockBean
    private lateinit var mdFactory: MdFactory

    @MockBean
    private lateinit var fxService: FxRateService

    @MockBean
    private lateinit var cashServices: CashServices

    private val kafkaTestUtils = KafkaConsumerUtils()

    private val tradeDateString = "2020-01-01"
    private val tradeDate = dateUtils.getDate(tradeDateString)

    @Test
    fun exportFileImported() {
        mockEnv()

        val portfolios: Collection<PortfolioInput> = arrayListOf(
            PortfolioInput(
                code = "CSV-FLOW",
                name = "SomeName",
                base = USD.code
            )
        )
        val pfResponse = portfolioService.save(portfolios)
        assertThat(pfResponse).isNotNull.hasSize(1)
        val portfolio = pfResponse.iterator().next()
        `when`(fxService.getRates(any())).thenReturn(
            FxResponse(
                FxPairResults(
                    mapOf(Pair(IsoCurrencyPair(USD.code, ""), FxRate(USD, USD, date = "2000-01-01")))
                )
            )
        )
//        `when`(enrichmentFactory.getEnricher(any()))
//            .thenReturn(Mockito.mock(AssetEnricher::class.java))
        val provider = "BC"
        val batch = "batch"
        val qcom = getAsset("QCOM", CUSTOM)
        val trex = getAsset("TREX", CUSTOM)
        val trnRequest = TrnRequest(
            portfolio.id,
            arrayOf(
                getTrnInput(qcom, CallerRef(provider, batch, "1")),
                getTrnInput(trex, CallerRef(provider, batch, "2"))
            )
        )
        val trnResponse = trnService.save(
            portfolio,
            trnRequest = trnRequest
        )

        assertThat(trnResponse.data).isNotNull.hasSize(trnRequest.data.size)

        val fileName = exportDelimitedFile(portfolio)

        purge(portfolio)
        val consumer = kafkaTestUtils.getConsumer(
            "is_ExportFileImported",
            TOPIC_CSV_IO,
            embeddedKafkaBroker
        )
        importRows(fileName, portfolio, trnRequest.data.size + 1) // Include a header row.
        assertThat(processQueue(consumer))
            .isNotNull
            .hasNoNullFieldsOrProperties()
        consumer.close()

        val imported = trnService.findForPortfolio(portfolio, dateUtils.date).data
        assertThat(imported).hasSize(trnRequest.data.size)
    }

    private fun purge(portfolio: Portfolio) {
        trnService.purge(portfolio)
        assertThat(trnService.findForPortfolio(portfolio, dateUtils.date).data).isEmpty()
    }

    private fun importRows(fileName: String, portfolio: Portfolio, expectedTrns: Int) {
        val csvRows = File(fileName).useLines { it.toList() }
        assertThat(csvRows).hasSize(expectedTrns)
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
    }

    private fun mockEnv() {
        val (id) = systemUserService.save(SystemUser("mike"))

        token = mockAuthConfig.getUserToken(SystemUser(id, Constants.systemUser.email))

        `when`(tokenService.subject).thenReturn(id)

        RegistrationUtils.registerUser(
            mockMvc,
            token
        )

        assertThat(currencyService.currencies).isNotEmpty
        assertThat(eventWriter.kafkaEnabled).isTrue
        val rateResponse = ClassPathResource("mock/fx/fx-current-rates.json").file

        stubFx(
            "/v1/$tradeDateString?base=USD&symbols=AUD%2CEUR%2CGBP%2CNZD%2CSGD%2CUSD&access_key=test",
            rateResponse
        )
    }

    private fun getTrnInput(asset: Asset, callerRef: CallerRef): TrnInput {
        return TrnInput(
            callerRef,
            asset.id,
            trnType = BUY,
            quantity = BigDecimal.TEN,
            fees = ONE,
            tradeDate = tradeDate,
            cashCurrency = asset.market.currency.code,
            price = BigDecimal(5.99),
        )
    }

    private fun exportDelimitedFile(forPortfolio: Portfolio): String {
        val results = mockMvc.perform(
            MockMvcRequestBuilders.get("/trns/portfolio/{portfolioId}/export", forPortfolio.id)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token)
                )
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN_VALUE))
            .andReturn()
        assertThat(results).isNotNull
        val fileName = "${forPortfolio.code}.txt"
        File(fileName).bufferedWriter().use { out ->
            out.write(results.response.contentAsString)
        }
        return fileName
    }

    private fun getAsset(code: String, market: Market): Asset {
        val asset = AssetRequest(getAssetInput(market.code, code), code)
        val response = assetService.handle(asset)!!
        assertThat(response).isNotNull
        return response.data[code]!!
    }

    private fun processQueue(consumer: Consumer<String, String>): TrnResponse {
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

    companion object {
        const val TOPIC_CSV_IO = "topicCsvIo"
        private val log = LoggerFactory.getLogger(KafkaTrnTest::class.java)
        @JvmStatic
        fun stubFx(url: String, rateResponse: File) {
            WireMock.stubFor(
                WireMock.get(WireMock.urlEqualTo(url))
                    .willReturn(
                        WireMock.aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(
                                BcJson().objectMapper.writeValueAsString(
                                    BcJson().objectMapper.readValue(
                                        rateResponse,
                                        HashMap::class.java
                                    )
                                )
                            )
                            .withStatus(200)
                    )
            )
        }
    }
}
