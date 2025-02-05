package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.aaplInput
import com.beancounter.marketdata.Constants.Companion.msftInput
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils
import com.beancounter.marketdata.utils.TRNS_ROOT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * TRN Mvc Controller API Tests
 */
@SpringMvcDbTest
class TrnControllerTest {
    private val dateUtils = DateUtils()

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var token: Jwt

    private lateinit var bcMvcHelper: BcMvcHelper
    private lateinit var msft: Asset
    private lateinit var aapl: Asset

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(DefaultEnricher())
        assertThat(currencyService.currencies()).isNotEmpty

        token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                token
            )

        RegistrationUtils.registerUser(
            mockMvc,
            token
        )
        assertThat(figiProxy).isNotNull
        msft =
            bcMvcHelper.asset(
                AssetRequest(msftInput)
            )
        aapl =
            bcMvcHelper.asset(
                AssetRequest(aaplInput)
            )
    }

    @Test
    fun is_EmptyResponseValid() {
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    "ANY-CODE",
                    "is_EmptyResponseValid",
                    currency = NZD.code
                )
            )
        val mvcResult =
            mockMvc
                .perform(
                    get(
                        "$TRNS_ROOT/portfolio/{portfolioId}/{asAt}",
                        portfolio.id,
                        dateUtils.today()
                    ).with(
                        SecurityMockMvcRequestPostProcessors
                            .jwt()
                            .jwt(token)
                    )
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
                .andReturn()
        val body = mvcResult.response.contentAsString
        assertThat(body).isNotNull
        assertThat(
            objectMapper
                .readValue(
                    body,
                    TrnResponse::class.java
                ).data
        ).isNotNull
            .hasSize(0)
    }

    @Test
    fun is_ExistingDividendFound() {
        val portfolioA =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    "DIV-TEST",
                    "is_ExistingDividendFound",
                    currency = NZD.code
                )
            )
        // Creating in random order and assert retrieved in Sort Order.
        val trnInput =
            TrnInput(
                CallerRef(
                    batch = "DIV-TEST",
                    callerId = "1"
                ),
                msft.id,
                trnType = TrnType.DIVI,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate("2020-03-10"),
                price = BigDecimal.TEN
            )
        val existingTrns = arrayOf(trnInput)
        val trnRequest =
            TrnRequest(
                portfolioA.id,
                existingTrns
            )
        trnService.save(
            portfolioA,
            trnRequest
        )
        val divi = existingTrns.iterator().next()

        val trustedTrnEvent =
            TrustedTrnEvent(
                portfolioA,
                trnInput = divi
            )
        assertThat(trnService.existing(trustedTrnEvent)).isNotNull.isNotEmpty

        // Record date is earlier than an existing trn trade date
        assertThat(trnService.existing(trustedTrnEvent))
            .isNotNull.isNotEmpty // Within 20 days of proposed trade date
        assertThat(trnService.existing(trustedTrnEvent))
            .isNotNull.isNotEmpty // Within 20 days of proposed trade date

        val findByAsset =
            mockMvc
                .perform(
                    get(
                        "$TRNS_ROOT/{portfolioId}/asset/{assetId}/events",
                        portfolioA.id,
                        msft.id
                    ).contentType(APPLICATION_JSON)
                        .with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(token)
                        )
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(
                    MockMvcResultMatchers
                        .content()
                        .contentType(APPLICATION_JSON)
                ).andReturn()
        val trnResponse =
            objectMapper
                .readValue(
                    findByAsset.response.contentAsString,
                    TrnResponse::class.java
                )
        assertThat(trnResponse.data).isNotEmpty.hasSize(1) // 1 MSFT dividend
    }

    @Test
    fun is_findThrowingForIllegalTrnId() {
        bcMvcHelper.portfolio(
            PortfolioInput(
                "ILLEGAL",
                "is_findThrowingForIllegalTrnId",
                currency = NZD.code
            )
        )

        mockMvc
            .perform(
                get(
                    "$TRNS_ROOT/{trnId}",
                    "x123x"
                ).with(
                    SecurityMockMvcRequestPostProcessors
                        .jwt()
                        .jwt(token)
                )
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_PROBLEM_JSON))
            .andReturn()
    }

    @Test
    fun is_deleteThrowingForIllegalTrnId() {
        mockMvc
            .perform(
                delete(
                    "$TRNS_ROOT/{trnId}",
                    "illegalTrnId"
                ).with(
                    SecurityMockMvcRequestPostProcessors
                        .jwt()
                        .jwt(token)
                )
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_PROBLEM_JSON))
            .andReturn()
    }
}