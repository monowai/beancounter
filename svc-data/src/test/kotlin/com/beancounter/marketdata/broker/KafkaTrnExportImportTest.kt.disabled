package com.beancounter.marketdata.broker

import com.beancounter.auth.AuthUtilService
import com.beancounter.client.Assets
import com.beancounter.client.ingest.FxTransactions
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
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.CUSTOM
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcKafkaTest
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.CashTrnServices
import com.beancounter.marketdata.trn.TrnImportService
import com.beancounter.marketdata.trn.TrnService
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock
import org.apache.kafka.clients.consumer.Consumer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.io.File
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO
import java.time.Duration

/**
 * Check we can import the CSV file we export, via Kafka.
 */
@SpringMvcKafkaTest
class KafkaTrnExportImportTest {
    final var dateUtils: DateUtils = DateUtils()

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    // Setup so that the wiring is tested
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var marketDataService: MarketDataService

    @Autowired
    lateinit var assets: Assets

    @Autowired
    lateinit var portfolioService: PortfolioService

    @Autowired
    lateinit var systemUserService: SystemUserService

    @Autowired
    lateinit var trnImportService: TrnImportService

    @Autowired
    lateinit var trnService: TrnService

    @Autowired
    lateinit var eventProducer: EventProducer

    @Autowired
    lateinit var currencyService: CurrencyService

    @MockitoBean
    private lateinit var mdFactory: MdFactory

    @MockitoBean
    private lateinit var fxService: FxRateService

    @MockitoBean
    private lateinit var cashTrnServices: CashTrnServices

    @Autowired
    private lateinit var authUtilService: AuthUtilService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<Any, TrustedTrnImportRequest>

    private val kafkaTestUtils = KafkaConsumerUtils()

    private val tradeDateString = "2020-01-01"
    private val tradeDate = dateUtils.getFormattedDate(tradeDateString)

    @BeforeEach
    fun mockEnv() {
        assertThat(fxTransactions).isNotNull
        assertThat(mdFactory).isNotNull
        `when`(
            cashTrnServices.getCashImpact(
                any(),
                any()
            )
        ).thenReturn(ZERO)
        assertThat(currencyService.currencies()).isNotEmpty
        assertThat(eventProducer.kafkaEnabled).isTrue
        val rateResponse = ClassPathResource("mock/fx/fx-current-rates.json").file

        stubFx(
            "/v1/$tradeDateString?base=USD&symbols=AUD%2CEUR%2CGBP%2CNZD%2CSGD%2CUSD&access_key=test",
            rateResponse
        )
        `when`(
            fxService.getRates(
                any(),
                any()
            )
        ).thenReturn(
            FxResponse(
                FxPairResults(
                    mapOf(
                        Pair(
                            IsoCurrencyPair(
                                USD.code,
                                ""
                            ),
                            FxRate(
                                from = USD,
                                to = USD,
                                date = dateUtils.getDate("2000-01-01")
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun exportFileCanBeImported() {
        val portfolios: Collection<PortfolioInput> =
            arrayListOf(
                PortfolioInput(
                    code = "CSV-FLOW",
                    name = "SomeName",
                    base = USD.code
                )
            )
        val (_, token) = loginMike()

        val pfResponse = portfolioService.save(portfolios)
        assertThat(pfResponse).isNotNull.hasSize(1)
        val portfolio = pfResponse.iterator().next()
        val provider = "BC"
        val batch = "batch"
        val qcom =
            getAsset(
                "QCOM",
                CUSTOM
            )
        val trex =
            getAsset(
                "TREX",
                CUSTOM
            )
        val trnRequest =
            TrnRequest(
                portfolio.id,
                arrayOf(
                    getTrnInput(
                        qcom,
                        CallerRef(
                            provider,
                            batch,
                            "1"
                        )
                    ),
                    getTrnInput(
                        trex,
                        CallerRef(
                            provider,
                            batch,
                            "2"
                        )
                    )
                )
            )

        val trnResponse =
            trnService.save(
                portfolio,
                trnRequest = trnRequest
            )

        assertThat(trnResponse)
            .isNotNull
            .hasSize(trnRequest.data.size)

        val consumer =
            kafkaTestUtils.getConsumer(
                this::class.toString(),
                TOPIC_CSV_IO,
                embeddedKafkaBroker
            )

        importRows(
            exportDelimitedFile(
                portfolio,
                token
            ),
            portfolio,
            trnRequest.data.size + 1
        ) // Include a header row.

        assertThat(processQueue(consumer))
            .isNotNull
            .hasNoNullFieldsOrProperties()
        consumer.close()
        loginMike()
        val imported =
            trnService.findForPortfolio(
                portfolio,
                dateUtils.date
            )
        assertThat(imported).hasSize(trnRequest.data.size)
    }

    private fun loginMike(): Pair<SystemUser, Jwt> {
        val systemUser = SystemUser("mike")
        val token =
            authUtilService
                .authenticate(
                    systemUser,
                    AuthUtilService.AuthProvider.AUTH0
                ).token
        systemUserService.register()
        return Pair(
            systemUser,
            token
        )
    }

    private fun importRows(
        fileName: String,
        portfolio: Portfolio,
        expectedTrns: Int
    ) {
        trnService.purge(portfolio)
        loginMike()
        assertThat(
            trnService.findForPortfolio(
                portfolio,
                dateUtils.date
            )
        ).isEmpty()
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
                val bcRequest =
                    TrustedTrnImportRequest(
                        portfolio,
                        row = splitRow
                    )
                log.info(
                    "Sending {}, {}, {}",
                    splitRow[0],
                    splitRow[1],
                    splitRow[2]
                )
                kafkaTemplate
                    .send(
                        TOPIC_CSV_IO,
                        bcRequest
                    ).get()
            }
        }
    }

    private fun getTrnInput(
        asset: Asset,
        callerRef: CallerRef
    ): TrnInput =
        TrnInput(
            callerRef,
            asset.id,
            trnType = BUY,
            quantity = BigDecimal.TEN,
            fees = ONE,
            tradeDate = tradeDate,
            cashCurrency = asset.market.currency.code,
            price = BigDecimal(5.99)
        )

    private fun exportDelimitedFile(
        forPortfolio: Portfolio,
        token: Jwt
    ): String {
        val results =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "/trns/portfolio/{portfolioId}/export",
                            forPortfolio.id
                        ).with(
                            SecurityMockMvcRequestPostProcessors.jwt().jwt(token)
                        ).with(SecurityMockMvcRequestPostProcessors.csrf())
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN_VALUE))
                .andReturn()
        assertThat(results).isNotNull
        val fileName = "./build/${forPortfolio.code}.txt"
        File(fileName).bufferedWriter().use { out ->
            out.write(results.response.contentAsString)
        }
        return fileName
    }

    private fun getAsset(
        code: String,
        market: Market
    ): Asset {
        val asset =
            AssetRequest(
                getAssetInput(
                    market.code,
                    code
                ),
                code
            )
        val response = assets.handle(asset)!!
        assertThat(response).isNotNull
        return response.data[code]!!
    }

    private fun processQueue(consumer: Consumer<String, String>): TrnResponse {
        val consumerRecords =
            KafkaTestUtils.getRecords(
                consumer,
                Duration.ofSeconds(5),
                2
            )
        val created = arrayListOf<Trn>()
        for (consumerRecord in consumerRecords) {
            assertThat(consumerRecord.value()).isNotNull
            val received =
                objectMapper
                    .readValue<TrustedTrnImportRequest>(consumerRecord.value())
            val trnResponse = trnImportService.fromCsvImport(received)
            assertThat(trnResponse).isNotNull
            assertThat(trnResponse)
                .isNotNull
                .hasSize(1)
            val found = trnResponse.iterator().next()
            log.info(
                "found {}",
                found.callerRef
            )
            created.add(found)
        }
        return TrnResponse(created)
    }

    companion object {
        const val TOPIC_CSV_IO = "topicCsvIo"
        private val log = LoggerFactory.getLogger(KafkaTrnExportImportTest::class.java)

        @JvmStatic
        fun stubFx(
            url: String,
            rateResponse: File
        ) {
            WireMock.stubFor(
                WireMock
                    .get(WireMock.urlEqualTo(url))
                    .willReturn(
                        WireMock
                            .aResponse()
                            .withHeader(
                                HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE
                            ).withBody(
                                objectMapper.writeValueAsString(
                                    objectMapper.readValue(
                                        rateResponse,
                                        HashMap::class.java
                                    )
                                )
                            ).withStatus(200)
                    )
            )
        }
    }
}