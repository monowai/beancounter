package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * Tests transaction creation patterns used by svc-rebalance.
 *
 * These tests verify that svc-data can correctly handle the TrnInput
 * structure sent by svc-rebalance when committing execution transactions.
 */
@SpringMvcDbTest
class RebalanceTrnTest {
    private val dateUtils = DateUtils()

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var token: Jwt
    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(DefaultEnricher())
        assertThat(currencyService.currencies()).isNotEmpty

        token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper = BcMvcHelper(mockMvc, token)

        RegistrationUtils.registerUser(mockMvc, token)
    }

    @Test
    fun `should create BUY transaction with PROPOSED status like svc-rebalance`() {
        // Given a USD portfolio and a US asset (simulating rebalance scenario)
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "REBAL-TEST",
                    name = "Rebalance Test Portfolio",
                    currency = USD.code
                )
            )

        val asset =
            bcMvcHelper.asset(
                AssetRequest(
                    AssetInput(
                        market = "US",
                        code = "VOO",
                        name = "Vanguard S&P 500 ETF"
                    )
                )
            )

        // When creating a transaction like svc-rebalance does
        val trnInput =
            TrnInput(
                callerRef =
                    CallerRef(
                        provider = "REBALANCE",
                        batch = "exec-123",
                        callerId = "item-456"
                    ),
                assetId = asset.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal("4.0000"),
                tradeCurrency = USD.code,
                tradeDate = dateUtils.date,
                price = BigDecimal("500.00"),
                tradeAmount = BigDecimal("2000.00"),
                status = TrnStatus.PROPOSED,
                comments = "Rebalance: Test Model"
            )

        val trnRequest =
            TrnRequest(
                portfolioId = portfolio.id,
                data = listOf(trnInput)
            )

        val result =
            mockMvc
                .perform(
                    post(TRNS_ROOT)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(trnRequest))
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
                .andReturn()

        // Then the transaction should be created successfully
        val response =
            objectMapper.readValue(
                result.response.contentAsString,
                TrnResponse::class.java
            )

        assertThat(response.data).hasSize(1)
        val createdTrn = response.data.first()
        assertThat(createdTrn.trnType).isEqualTo(TrnType.BUY)
        assertThat(createdTrn.status).isEqualTo(TrnStatus.PROPOSED)
        assertThat(createdTrn.quantity).isEqualByComparingTo(BigDecimal("4.0000"))
        assertThat(createdTrn.price).isEqualByComparingTo(BigDecimal("500.00"))
    }

    @Test
    fun `should create BUY transaction for SGD portfolio with USD asset`() {
        // Given a SGD portfolio (common for Singapore investors)
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "SGD-REBAL",
                    name = "SGD Rebalance Portfolio",
                    currency = SGD.code
                )
            )

        // And a US-listed asset
        val asset =
            bcMvcHelper.asset(
                AssetRequest(
                    AssetInput(
                        market = "US",
                        code = "VTI",
                        name = "Vanguard Total Stock Market ETF"
                    )
                )
            )

        // When creating a transaction with USD trade currency
        val trnInput =
            TrnInput(
                callerRef =
                    CallerRef(
                        provider = "REBALANCE",
                        batch = "exec-sgd-001",
                        callerId = "item-001"
                    ),
                assetId = asset.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                tradeCurrency = USD.code, // Trade in USD even though portfolio is SGD
                tradeDate = dateUtils.date,
                price = BigDecimal("250.00"),
                tradeAmount = BigDecimal("2500.00"),
                status = TrnStatus.PROPOSED,
                comments = "Rebalance: SGD Portfolio Test"
            )

        val trnRequest =
            TrnRequest(
                portfolioId = portfolio.id,
                data = listOf(trnInput)
            )

        val result =
            mockMvc
                .perform(
                    post(TRNS_ROOT)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(trnRequest))
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        // Then the transaction should be created with FX conversion
        val response =
            objectMapper.readValue(
                result.response.contentAsString,
                TrnResponse::class.java
            )

        assertThat(response.data).hasSize(1)
        val createdTrn = response.data.first()
        assertThat(createdTrn.tradeCurrency.code).isEqualTo(USD.code)
        // FX rates should be populated by svc-data
        assertThat(createdTrn.tradeBaseRate).isNotNull()
    }
}