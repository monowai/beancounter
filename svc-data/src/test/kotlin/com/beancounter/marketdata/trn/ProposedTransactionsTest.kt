package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PortfolioSharesResponse
import com.beancounter.common.contracts.ShareInviteRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.msftInput
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
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
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var trnRepository: TrnRepository

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
        assertThat(response.data.trns).isNotEmpty
        assertThat(response.data.trns.filter { it.status == TrnStatus.PROPOSED }).hasSizeGreaterThanOrEqualTo(2)
    }

    @Test
    fun `proposed view and count exclude auto-settle cash legs`() {
        // A PROPOSED trade in a funded portfolio now emits PROPOSED BC-AUTO cash
        // legs. They are derivative of the parent (settle in sync), so must NOT
        // appear as independent items in the proposed/unsettled review.
        val msft = bcMvcHelper.asset(AssetRequest(msftInput))
        val usdCash =
            assetService
                .handle(AssetRequest(mapOf(USD.code to AssetUtils.getCash(USD.code))))
                .data[USD.code]!!

        val master =
            bcMvcHelper.portfolio(
                PortfolioInput("AUTOLEG-MASTER", "Master", currency = USD.code)
            )
        val invest =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    "AUTOLEG-INV",
                    "Invest",
                    currency = USD.code,
                    cashPortfolioId = master.id
                )
            )

        val buy =
            TrnInput(
                CallerRef(batch = "AUTOLEG", callerId = "autoleg-parent"),
                msft.id,
                cashAssetId = usdCash.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.ONE,
                tradeCurrency = USD.code,
                cashCurrency = USD.code,
                cashAmount = BigDecimal("-100"),
                tradeCashRate = BigDecimal.ONE,
                tradeDate = dateUtils.getFormattedDate("2024-03-10"),
                price = BigDecimal("100"),
                status = TrnStatus.PROPOSED
            )
        trnService.save(invest, TrnRequest(invest.id, listOf(buy)))

        // Guard: the two PROPOSED BC-AUTO legs really exist in the DB.
        val autoLegs =
            trnRepository.findByCallerRefProviderAndCallerRefBatch("BC-AUTO", "autoleg-parent")
        assertThat(autoLegs)
            .hasSize(2)
            .allSatisfy { assertThat(it.status).isEqualTo(TrnStatus.PROPOSED) }

        val result =
            mockMvc
                .perform(
                    get("$TRNS_ROOT/proposed")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
        val response =
            objectMapper.readValue(result.response.contentAsString, TrnResponse::class.java)

        // Parent trade is shown; neither BC-AUTO leg is.
        assertThat(response.data.trns)
            .anyMatch { it.callerRef?.callerId == "autoleg-parent" && it.trnType == TrnType.BUY }
        assertThat(response.data.trns).noneMatch { it.callerRef?.provider == "BC-AUTO" }

        // Count matches the filtered list (no auto legs inflating it).
        val countResult =
            mockMvc
                .perform(
                    get("$TRNS_ROOT/proposed/count")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
        val count = objectMapper.readTree(countResult.response.contentAsString).get("count").asInt()
        assertThat(count).isEqualTo(response.data.trns.size)
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
    fun `scope MANAGED returns proposed transactions on portfolios shared with the user`() {
        // Client owns a portfolio with a PROPOSED transaction. An adviser is granted
        // ACTIVE share access. The adviser must see the trn under scope=MANAGED, while
        // scope=OWNED returns nothing for the adviser, and ALL returns the trn.

        val adviser =
            SystemUser(
                id = "managed-adviser",
                email = "managed-adviser@test.com",
                auth0 = "auth0|managed-adviser"
            )
        val adviserToken = mockAuthConfig.getUserToken(adviser)
        RegistrationUtils.registerUser(mockMvc, adviserToken)

        val msft = bcMvcHelper.asset(AssetRequest(msftInput))
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput("PROPOSED-MANAGED", "Managed", currency = USD.code)
            )

        val trnInput =
            TrnInput(
                CallerRef(batch = "MANAGED-PROPOSED", callerId = "1"),
                msft.id,
                trnType = TrnType.SELL,
                quantity = BigDecimal("5"),
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate("2024-04-10"),
                price = BigDecimal("180.00"),
                status = TrnStatus.PROPOSED
            )
        trnService.save(portfolio, TrnRequest(portfolio.id, listOf(trnInput)))

        val invite =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = adviser.email
            )
        val inviteResult =
            mockMvc
                .perform(
                    post("/shares/invite")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(invite))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
        val shareId =
            objectMapper
                .readValue(inviteResult.response.contentAsString, PortfolioSharesResponse::class.java)
                .data
                .first()
                .id
        mockMvc
            .perform(
                post("/shares/$shareId/accept")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adviserToken))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk)

        val managed =
            objectMapper.readValue(
                mockMvc
                    .perform(
                        get("$TRNS_ROOT/proposed?scope=MANAGED")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adviserToken))
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturn()
                    .response.contentAsString,
                TrnResponse::class.java
            )
        assertThat(managed.data.toTrns())
            .anySatisfy { trn ->
                assertThat(trn.portfolio.id).isEqualTo(portfolio.id)
                assertThat(trn.status).isEqualTo(TrnStatus.PROPOSED)
            }

        val owned =
            objectMapper.readValue(
                mockMvc
                    .perform(
                        get("$TRNS_ROOT/proposed?scope=OWNED")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adviserToken))
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturn()
                    .response.contentAsString,
                TrnResponse::class.java
            )
        assertThat(owned.data.toTrns()).noneMatch { it.portfolio.id == portfolio.id }

        val all =
            objectMapper.readValue(
                mockMvc
                    .perform(
                        get("$TRNS_ROOT/proposed?scope=ALL")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adviserToken))
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturn()
                    .response.contentAsString,
                TrnResponse::class.java
            )
        assertThat(all.data.toTrns()).anyMatch { it.portfolio.id == portfolio.id }

        val countNode =
            objectMapper.readTree(
                mockMvc
                    .perform(
                        get("$TRNS_ROOT/proposed/count?scope=MANAGED")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adviserToken))
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturn()
                    .response.contentAsString
            )
        assertThat(countNode.get("count").asInt()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `proposed list bounds by asAt date — future-dated trns hidden by default, shown when asAt covers them`() {
        val msft = bcMvcHelper.asset(AssetRequest(msftInput))
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput("PROPOSED-FUTURE", "Future-dated proposed", currency = USD.code)
            )
        // A DIVI whose pay date (tradeDate) is in the future — not yet due for review.
        val futureDate = dateUtils.date.plusDays(8)
        val futureTrn =
            TrnInput(
                CallerRef(batch = "FUTURE-PROPOSED", callerId = "1"),
                msft.id,
                trnType = TrnType.DIVI,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = futureDate,
                price = BigDecimal("1.50"),
                status = TrnStatus.PROPOSED
            )
        trnService.save(portfolio, TrnRequest(portfolio.id, listOf(futureTrn)))

        // Default (asAt = today) excludes the future-dated trn.
        val defaultTrns = proposedFor(portfolio.id, asAt = null)
        assertThat(defaultTrns).isEmpty()

        // asAt on/after the trade date includes it.
        val futureTrns = proposedFor(portfolio.id, asAt = futureDate.toString())
        assertThat(futureTrns).hasSize(1)

        // The count badge tracks the same default-today bound.
        val defaultCount = proposedCount(asAt = null)
        val futureCount = proposedCount(asAt = futureDate.toString())
        assertThat(futureCount).isGreaterThan(defaultCount)
    }

    private fun proposedFor(
        portfolioId: String,
        asAt: String?
    ): List<com.beancounter.common.model.Trn> {
        val url = "$TRNS_ROOT/proposed" + (asAt?.let { "?asAt=$it" } ?: "")
        val body =
            mockMvc
                .perform(get(url).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response.contentAsString
        return objectMapper
            .readValue(body, TrnResponse::class.java)
            .data
            .toTrns()
            .filter { it.portfolio.id == portfolioId }
    }

    private fun proposedCount(asAt: String?): Int {
        val url = "$TRNS_ROOT/proposed/count" + (asAt?.let { "?asAt=$it" } ?: "")
        val body =
            mockMvc
                .perform(get(url).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(body).get("count").asInt()
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
        assertThat(response.data.trns.all { it.status == TrnStatus.PROPOSED }).isTrue()
    }
}