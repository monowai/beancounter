package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * Tests for proposed transactions endpoints.
 *
 * Tests the cross-portfolio proposed transactions functionality:
 * - GET /trns/proposed - returns all PROPOSED transactions for the current user
 * - GET /trns/proposed/count - returns the count of PROPOSED transactions
 */
@SpringMvcDbTest
class ProposedTransactionsTest {
    private val dateUtils = DateUtils()

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var token: Jwt
    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(defaultEnricher)
        assertThat(currencyService.currencies()).isNotEmpty

        token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper = BcMvcHelper(mockMvc, token)

        RegistrationUtils.registerUser(mockMvc, token)
    }

    @Test
    fun `should return proposed transactions across all portfolios`() {
        // Given: Two portfolios with proposed transactions
        val msft = bcMvcHelper.asset(AssetRequest(msftInput))

        val portfolio1 =
            bcMvcHelper.portfolio(
                PortfolioInput("PROPOSED-P1", "Portfolio 1 for Proposed", currency = USD.code)
            )
        val portfolio2 =
            bcMvcHelper.portfolio(
                PortfolioInput("PROPOSED-P2", "Portfolio 2 for Proposed", currency = USD.code)
            )

        // Create PROPOSED transactions in both portfolios
        val trnInput1 =
            TrnInput(
                CallerRef(batch = "PROPOSED-TEST", callerId = "1"),
                msft.id,
                trnType = TrnType.DIVI,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate("2024-03-10"),
                price = BigDecimal("1.50"),
                status = TrnStatus.PROPOSED
            )
        val trnInput2 =
            TrnInput(
                CallerRef(batch = "PROPOSED-TEST", callerId = "2"),
                msft.id,
                trnType = TrnType.DIVI,
                quantity = BigDecimal("20"),
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate("2024-03-15"),
                price = BigDecimal("2.00"),
                status = TrnStatus.PROPOSED
            )

        trnService.save(portfolio1, TrnRequest(portfolio1.id, listOf(trnInput1)))
        trnService.save(portfolio2, TrnRequest(portfolio2.id, listOf(trnInput2)))

        // When: Fetching all proposed transactions
        val result =
            mockMvc
                .perform(
                    get("$TRNS_ROOT/proposed")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
                .andReturn()

        // Then: Both transactions should be returned
        val response =
            objectMapper.readValue(
                result.response.contentAsString,
                TrnResponse::class.java
            )
        assertThat(response.data).isNotEmpty
        assertThat(response.data.filter { it.status == TrnStatus.PROPOSED }).hasSizeGreaterThanOrEqualTo(2)
    }

    @Test
    fun `should return proposed transaction count across all portfolios`() {
        // Given: A portfolio with proposed transactions
        val msft = bcMvcHelper.asset(AssetRequest(msftInput))

        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput("PROPOSED-COUNT", "Portfolio for Count", currency = USD.code)
            )

        // Create PROPOSED transaction
        val trnInput =
            TrnInput(
                CallerRef(batch = "PROPOSED-COUNT", callerId = "1"),
                msft.id,
                trnType = TrnType.DIVI,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate("2024-03-10"),
                price = BigDecimal("1.50"),
                status = TrnStatus.PROPOSED
            )
        trnService.save(portfolio, TrnRequest(portfolio.id, listOf(trnInput)))

        // When: Fetching proposed transaction count
        val result =
            mockMvc
                .perform(
                    get("$TRNS_ROOT/proposed/count")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
                .andReturn()

        // Then: Count should be at least 1
        val count = objectMapper.readTree(result.response.contentAsString).get("count").asInt()
        assertThat(count).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `should return zero count when no proposed transactions exist`() {
        // Given: A new user with no transactions
        val newUserToken =
            mockAuthConfig.getUserToken(
                Constants.systemUser.copy(id = "new-user-proposed", email = "proposed@test.com")
            )
        RegistrationUtils.registerUser(mockMvc, newUserToken)

        // When: Fetching proposed transaction count
        val result =
            mockMvc
                .perform(
                    get("$TRNS_ROOT/proposed/count")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(newUserToken))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
                .andReturn()

        // Then: Count should be 0
        val count = objectMapper.readTree(result.response.contentAsString).get("count").asInt()
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `should not include SETTLED transactions in proposed list`() {
        // Given: A portfolio with both PROPOSED and SETTLED transactions
        val msft = bcMvcHelper.asset(AssetRequest(msftInput))

        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput("PROPOSED-FILTER", "Portfolio for Filter", currency = USD.code)
            )

        val proposedTrn =
            TrnInput(
                CallerRef(batch = "FILTER-TEST", callerId = "1"),
                msft.id,
                trnType = TrnType.DIVI,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate("2024-03-10"),
                price = BigDecimal("1.50"),
                status = TrnStatus.PROPOSED
            )
        val settledTrn =
            TrnInput(
                CallerRef(batch = "FILTER-TEST", callerId = "2"),
                msft.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal("100"),
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate("2024-03-01"),
                price = BigDecimal("150.00"),
                status = TrnStatus.SETTLED
            )

        trnService.save(portfolio, TrnRequest(portfolio.id, listOf(proposedTrn, settledTrn)))

        // When: Fetching proposed transactions
        val result =
            mockMvc
                .perform(
                    get("$TRNS_ROOT/proposed")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        // Then: Only PROPOSED transactions should be returned
        val response =
            objectMapper.readValue(
                result.response.contentAsString,
                TrnResponse::class.java
            )
        assertThat(response.data.all { it.status == TrnStatus.PROPOSED }).isTrue()
    }
}