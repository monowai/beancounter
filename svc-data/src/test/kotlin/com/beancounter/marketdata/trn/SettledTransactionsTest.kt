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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * Tests for the settled transactions endpoint GET /trns/settled.
 *
 * The Transactions review page filters by a From–To date window, so the
 * endpoint accepts a [from]/[to] range (legacy single-date `tradeDate` still
 * supported). Settled trns whose tradeDate falls inside the window are
 * returned; those outside are excluded.
 */
@SpringMvcDbTest
class SettledTransactionsTest {
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

    private fun settledBuy(
        callerId: String,
        tradeDate: String
    ): TrnInput {
        val msft = bcMvcHelper.asset(AssetRequest(msftInput))
        return TrnInput(
            CallerRef(batch = "SETTLED-RANGE", callerId = callerId),
            msft.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal("100"),
            tradeCurrency = USD.code,
            tradeDate = dateUtils.getFormattedDate(tradeDate),
            price = BigDecimal("150.00"),
            status = TrnStatus.SETTLED
        )
    }

    private fun settled(query: String): List<com.beancounter.common.model.Trn> =
        objectMapper
            .readValue(
                mockMvc
                    .perform(
                        get("$TRNS_ROOT/settled?$query")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturn()
                    .response.contentAsString,
                TrnResponse::class.java
            ).data
            .toTrns()

    @Test
    fun `settled list returns only trns whose tradeDate is within the from-to range`() {
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput("SETTLED-RANGE", "Settled range", currency = USD.code)
            )
        // One inside the window, one before it, one after it.
        trnService.save(
            portfolio,
            TrnRequest(
                portfolio.id,
                listOf(
                    settledBuy("in", "2024-03-10"),
                    settledBuy("before", "2024-03-01"),
                    settledBuy("after", "2024-03-20")
                )
            )
        )

        val inRange = settled("from=2024-03-05&to=2024-03-15")
        assertThat(inRange.map { it.callerRef?.callerId }).contains("in")
        assertThat(inRange.map { it.callerRef?.callerId })
            .doesNotContain("before", "after")
    }

    @Test
    fun `settled list supports legacy single-date tradeDate param`() {
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput("SETTLED-LEGACY", "Settled legacy", currency = USD.code)
            )
        trnService.save(
            portfolio,
            TrnRequest(
                portfolio.id,
                listOf(
                    settledBuy("hit", "2024-04-10"),
                    settledBuy("miss", "2024-04-11")
                )
            )
        )

        val onDate = settled("tradeDate=2024-04-10")
        assertThat(onDate.map { it.callerRef?.callerId }).contains("hit")
        assertThat(onDate.map { it.callerRef?.callerId }).doesNotContain("miss")
    }
}