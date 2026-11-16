package com.beancounter.marketdata.broker.reconcile

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.BrokerInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.broker.BrokerConfig
import com.beancounter.marketdata.broker.GlobalBroker
import com.beancounter.marketdata.broker.GlobalBrokerRepository
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils
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

private const val BROKERS_ROOT = "/brokers"
private const val RECONCILE = "$BROKERS_ROOT/reconcile"

/**
 * `POST /brokers/reconcile` over HTTP: the shape a client reads, and the same
 * class-level guard as the other `/brokers` endpoints (see BrokerControllerAuthTest
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
}