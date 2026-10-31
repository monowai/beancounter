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
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnDto
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.msftInput
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.portfolioCreate
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Data scoped to a portfolio is answered only for a caller who owns it or holds
 * an active share over it. Each attack names a portfolio or trn it has no access to.
 */
@SpringMvcDbTest
internal class PortfolioAccessTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    private lateinit var ownerToken: Jwt
    private lateinit var otherToken: Jwt
    private lateinit var portfolio: Portfolio
    private lateinit var mine: Portfolio
    private lateinit var trn: TrnDto
    private lateinit var trnInput: TrnInput
    private lateinit var asset: Asset

    private fun user(name: String): Jwt =
        registerUser(
            mockMvc,
            mockAuthConfig.getUserToken(
                SystemUser(id = name, email = "$name@testing.com", auth0 = "auth0|$name")
            )
        )

    private fun create(
        code: String,
        token: Jwt
    ): Portfolio = portfolioCreate(PortfolioInput(code, code, USD.code, NZD.code), mockMvc, token).data.first()

    @BeforeEach
    fun setup() {
        ownerToken = user("access-owner")
        otherToken = user("access-other")
        val suffix = System.nanoTime()
        portfolio = create("OWN$suffix", ownerToken)
        mine = create("MINE$suffix", otherToken)
        enrichmentFactory.register(defaultEnricher)
        val helper = BcMvcHelper(mockMvc, ownerToken)
        asset = helper.asset(AssetRequest(msftInput))
        trnInput =
            TrnInput(
                CallerRef(batch = "ACCESS", callerId = "$suffix"),
                asset.id,
                trnType = TrnType.DIVI,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = LocalDate.parse("2020-03-10"),
                price = BigDecimal.TEN
            )
        trn =
            objectMapper
                .readValue(
                    helper.postTrn(TrnRequest(portfolio.id, listOf(trnInput))).response.contentAsString,
                    TrnResponse::class.java
                ).data
                .trns
                .first()
    }

    private fun readTrn(token: Jwt) = mockMvc.perform(get("/trns/${trn.id}").with(jwt().jwt(token)))

    private fun income(
        ids: String,
        token: Jwt
    ) = mockMvc.perform(get("/trns/income/monthly?currency=USD&portfolioIds=$ids").with(jwt().jwt(token)))

    private fun investment(
        ids: String,
        token: Jwt
    ) = mockMvc.perform(get("/trns/investments/monthly?currency=USD&portfolioIds=$ids").with(jwt().jwt(token)))

    private fun share() {
        val invite =
            mockMvc
                .perform(
                    post("/shares/invite")
                        .with(jwt().jwt(ownerToken))
                        .with(csrf())
                        .content(
                            objectMapper.writeValueAsBytes(
                                ShareInviteRequest(
                                    portfolioIds = listOf(portfolio.id),
                                    adviserEmail = "access-other@testing.com"
                                )
                            )
                        ).contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()
        val shareId =
            objectMapper
                .readValue(invite.response.contentAsString, PortfolioSharesResponse::class.java)
                .data
                .first()
                .id
        mockMvc
            .perform(post("/shares/$shareId/accept").with(jwt().jwt(otherToken)).with(csrf()))
            .andExpect(status().isOk)
    }

    @Test
    fun `should answer the owner`() {
        income(portfolio.id, ownerToken).andExpect(status().isOk)
        investment(portfolio.id, ownerToken).andExpect(status().isOk)
    }

    @Test
    fun `should not reveal income of a portfolio the caller cannot access`() {
        income(portfolio.id, otherToken).andExpect(status().isNotFound)
    }

    @Test
    fun `should not reveal investment of a portfolio the caller cannot access`() {
        investment(portfolio.id, otherToken).andExpect(status().isNotFound)
    }

    @Test
    fun `should refuse the whole request when one portfolio is out of reach`() {
        income("${mine.id},${portfolio.id}", otherToken).andExpect(status().isNotFound)
        investment("${mine.id},${portfolio.id}", otherToken).andExpect(status().isNotFound)
    }

    @Test
    fun `should refuse until a share is accepted, then answer the adviser`() {
        income(portfolio.id, otherToken).andExpect(status().isNotFound)

        share()

        income(portfolio.id, otherToken).andExpect(status().isOk)
        investment(portfolio.id, otherToken).andExpect(status().isOk)
    }

    @Test
    fun `should not reveal a transaction of a portfolio the caller cannot access`() {
        readTrn(otherToken).andExpect(status().isNotFound)

        readTrn(ownerToken).andExpect(status().isOk)
    }

    @Test
    fun `should answer an adviser reading a shared transaction`() {
        share()

        readTrn(otherToken).andExpect(status().isOk)
    }

    @Test
    fun `should not let a stranger take over a transaction through their own portfolio`() {
        mockMvc
            .perform(
                patch("/trns/${mine.id}/${trn.id}")
                    .with(jwt().jwt(otherToken))
                    .with(csrf())
                    .content(objectMapper.writeValueAsBytes(trnInput.copy(quantity = BigDecimal.ONE)))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isNotFound)

        val stored =
            objectMapper
                .readValue(
                    readTrn(ownerToken).andReturn().response.contentAsString,
                    TrnResponse::class.java
                ).data
                .trns
                .first()
        assertThat(stored.portfolioId).isEqualTo(portfolio.id)
        assertThat(stored.quantity).isEqualByComparingTo(BigDecimal.TEN)
    }

    private fun queryTrades(token: Jwt) =
        mockMvc.perform(
            post("/trns/query")
                .with(jwt().jwt(token))
                .with(csrf())
                .content(
                    objectMapper.writeValueAsBytes(
                        TrustedTrnQuery(portfolio, LocalDate.parse("2020-12-31"), asset.id)
                    )
                ).contentType(MediaType.APPLICATION_JSON)
        )

    @Test
    fun `should not trust a portfolio named in the body of a trade query`() {
        queryTrades(otherToken).andExpect(status().isNotFound)

        queryTrades(ownerToken).andExpect(status().isOk)
    }
}