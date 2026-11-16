package com.beancounter.marketdata.broker.reconcile

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.BrokerInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.broker.BrokerConfig
import com.beancounter.marketdata.broker.GlobalBroker
import com.beancounter.marketdata.broker.GlobalBrokerRepository
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

private const val BROKERS_ROOT = "/brokers"
private const val RECONCILE = "$BROKERS_ROOT/reconcile"
private const val COMMIT = "$RECONCILE/commit"

/**
 * `POST /brokers/reconcile` and `POST /brokers/reconcile/commit` over HTTP: the shape
 * a client reads, and the same class-level guard as the other `/brokers` endpoints (see BrokerControllerAuthTest
 * for why the refused caller is registered and holds the admin authority).
 */
@SpringMvcDbTest
internal class ReconcileControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var globalBrokerRepository: GlobalBrokerRepository

    @Autowired
    private lateinit var trnRepository: TrnRepository

    private fun registered(): Jwt =
        RegistrationUtils.registerUser(
            mockMvc,
            mockAuthConfig.getUserToken(SystemUser(id = "rcn-mvc", email = "rcn-mvc@test.com"))
        )

    private fun body(shown: ShownHoldings): ByteArray = objectMapper.writeValueAsBytes(shown)

    @Test
    fun `should return the preview for the caller's statement`() {
        val token = registered()
        val helper = BcMvcHelper(mockMvc, token)
        val portfolio = helper.portfolio(PortfolioInput(code = "RCN-MVC", name = "RCN-MVC", currency = USD.code))
        val asset = helper.asset(AssetRequest(AssetInput(NASDAQ.code, "RCNMVC1"), "RCNMVC1"))
        val firm =
            globalBrokerRepository.save(
                GlobalBroker(code = "RCN-MVC", name = "Rcn Mvc Firm", config = BrokerConfig(market = NASDAQ.code))
            )
        mockMvc
            .perform(
                post(BROKERS_ROOT)
                    .with(jwt().jwt(token))
                    .content(
                        objectMapper.writeValueAsBytes(BrokerInput(name = "RCN-MVC-B", accountNumber = "88-7731942"))
                    ).contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isCreated)
        val shown =
            ShownHoldings(
                portfolioId = portfolio.id,
                brokerName = "rcn mvc firm",
                accountNumber = "7731942",
                rows =
                    listOf(
                        ShownHolding(
                            code = "RCNMVC1",
                            quantity = BigDecimal("10"),
                            costBasis = BigDecimal("125.50"),
                            price = BigDecimal("20"),
                            marketValue = BigDecimal("300")
                        )
                    )
            )

        mockMvc
            .perform(
                post(RECONCILE)
                    .with(jwt().jwt(token))
                    .content(body(shown))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.globalBroker.id").value(firm.id))
            .andExpect(jsonPath("$.data.globalBroker.code").value("RCN-MVC"))
            .andExpect(jsonPath("$.data.broker.id").isNotEmpty)
            .andExpect(jsonPath("$.data.broker.name").value("RCN-MVC-B"))
            .andExpect(jsonPath("$.data.broker.accountNumber").value("88-7731942"))
            .andExpect(jsonPath("$.data.rows.length()").value(1))
            .andExpect(jsonPath("$.data.rows[0].code").value("RCNMVC1"))
            .andExpect(jsonPath("$.data.rows[0].market").value(NASDAQ.code))
            .andExpect(jsonPath("$.data.rows[0].assetId").value(asset.id))
            .andExpect(jsonPath("$.data.rows[0].shown").value(10))
            .andExpect(jsonPath("$.data.rows[0].held").value(0))
            .andExpect(jsonPath("$.data.rows[0].trnType").value("ADD"))
            .andExpect(jsonPath("$.data.rows[0].quantity").value(10))
            .andExpect(jsonPath("$.data.rows[0].cost").value(125.5))
            .andExpect(jsonPath("$.data.rows[0].status").value("READY"))
            .andExpect(jsonPath("$.data.rows[0].warnings[0]").value("MARKET_VALUE_MISMATCH"))
            .andExpect(jsonPath("$.data.notShown").isEmpty)
    }

    @Test
    fun `should refuse a token without user or system authority even when registered`() {
        val token = registered()

        mockMvc
            .perform(
                post(RECONCILE)
                    .with(jwt().jwt(token).authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_ADMIN)))
                    .content(body(ShownHoldings(portfolioId = "rcn-any")))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `should refuse a caller with no token`() {
        mockMvc
            .perform(
                post(RECONCILE)
                    .content(body(ShownHoldings(portfolioId = "rcn-any")))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `should record the confirmed rows and return the transactions`() {
        val token = registered()
        val helper = BcMvcHelper(mockMvc, token)
        val portfolio = helper.portfolio(PortfolioInput(code = "RCN-MVC-C", name = "RCN-MVC-C", currency = USD.code))
        val asset = helper.asset(AssetRequest(AssetInput(NASDAQ.code, "RCNMVC2"), "RCNMVC2"))
        val created =
            mockMvc
                .perform(
                    post(BROKERS_ROOT)
                        .with(jwt().jwt(token))
                        .content(objectMapper.writeValueAsBytes(BrokerInput(name = "RCN-MVC-C-B")))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isCreated)
                .andReturn()
        val brokerId =
            objectMapper
                .readTree(created.response.contentAsByteArray)
                .path("data")
                .path("id")
                .asString()
        val confirmed =
            ConfirmedHoldings(
                portfolioId = portfolio.id,
                brokerId = brokerId,
                uploadId = "rcn-mvc-upload",
                tradeDate = LocalDate.of(2026, 3, 2),
                rows = listOf(ConfirmedRow(asset.id, TrnType.ADD, BigDecimal("10"), BigDecimal("125.50")))
            )

        mockMvc
            .perform(
                post(COMMIT)
                    .with(jwt().jwt(token))
                    .content(objectMapper.writeValueAsBytes(confirmed))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.trns.length()").value(1))
            .andExpect(jsonPath("$.data.trns[0].id").isNotEmpty)
            .andExpect(jsonPath("$.data.trns[0].trnType").value("ADD"))
            .andExpect(jsonPath("$.data.trns[0].status").value("SETTLED"))
            .andExpect(jsonPath("$.data.trns[0].assetId").value(asset.id))
            .andExpect(jsonPath("$.data.trns[0].portfolioId").value(portfolio.id))
            .andExpect(jsonPath("$.data.trns[0].brokerId").value(brokerId))
            .andExpect(jsonPath("$.data.trns[0].quantity").value(10))
            .andExpect(jsonPath("$.data.trns[0].tradeAmount").value(125.5))
            .andExpect(jsonPath("$.data.trns[0].tradeDate").value("2026-03-02"))
            .andExpect(jsonPath("$.data.trns[0].callerRef.provider").value("SCREENSHOT"))
            .andExpect(jsonPath("$.data.trns[0].callerRef.batch").value("rcn-mvc-upload"))
            .andExpect(jsonPath("$.warnings").isEmpty)

        val stored = trnRepository.findByCallerRefProviderAndCallerRefBatch("SCREENSHOT", "rcn-mvc-upload").single()
        assertThat(stored.portfolio.id).isEqualTo(portfolio.id)
        assertThat(stored.quantity).isEqualByComparingTo("10")
    }

    @Test
    fun `should refuse to record for a caller with no token`() {
        val confirmed = ConfirmedHoldings("rcn-any", "rcn-any", "rcn-any-upload", rows = emptyList())

        mockMvc
            .perform(
                post(COMMIT)
                    .content(objectMapper.writeValueAsBytes(confirmed))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `should refuse to record for a token without user or system authority`() {
        val token = registered()
        val confirmed = ConfirmedHoldings("rcn-any", "rcn-any", "rcn-any-upload", rows = emptyList())

        mockMvc
            .perform(
                post(COMMIT)
                    .with(jwt().jwt(token).authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_ADMIN)))
                    .content(objectMapper.writeValueAsBytes(confirmed))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isForbidden)
    }
}