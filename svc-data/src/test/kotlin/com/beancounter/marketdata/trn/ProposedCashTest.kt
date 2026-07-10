package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.TRNS_ROOT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * MVC tests for GET /trns/portfolio/{id}/proposed-cash — the earmarked-cash feed.
 * Only PROPOSED DEPOSIT/WITHDRAWAL legs due on or before asAt are returned:
 * SETTLED legs, BUY/SELL trades and future-dated legs are excluded.
 */
@SpringMvcDbTest
class ProposedCashTest {
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
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(defaultEnricher)
        bcMvcHelper = BcMvcHelper(mockMvc, mockAuthConfig.getUserToken(Constants.systemUser))
        bcMvcHelper.registerUser()
    }

    @Test
    fun `returns only PROPOSED deposit and withdrawal legs bounded by asAt`() {
        val usdCash =
            assetService
                .handle(AssetRequest(mapOf(USD.code to AssetUtils.getCash(USD.code))))
                .data[USD.code]!!
        val equity =
            assetService
                .handle(AssetRequest(AssetInput(NYSE.code, MSFT.code), MSFT.code))
                .data[MSFT.code]!!

        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput("PROPOSED-CASH", "Proposed cash", currency = USD.code)
            )

        val proposedDeposit =
            TrnInput(
                CallerRef(batch = "PC", callerId = "1"),
                assetId = usdCash.id,
                cashAssetId = usdCash.id,
                cashCurrency = USD.code,
                trnType = TrnType.DEPOSIT,
                quantity = BigDecimal("300"),
                tradeAmount = BigDecimal("300"),
                price = BigDecimal.ONE,
                tradeDate = dateUtils.getFormattedDate("2024-03-01"),
                status = TrnStatus.PROPOSED
            )
        val proposedWithdrawal =
            TrnInput(
                CallerRef(batch = "PC", callerId = "2"),
                assetId = usdCash.id,
                cashAssetId = usdCash.id,
                cashCurrency = USD.code,
                trnType = TrnType.WITHDRAWAL,
                quantity = BigDecimal("100"),
                tradeAmount = BigDecimal("100"),
                price = BigDecimal.ONE,
                tradeDate = dateUtils.getFormattedDate("2024-03-02"),
                status = TrnStatus.PROPOSED
            )
        val settledDeposit =
            TrnInput(
                CallerRef(batch = "PC", callerId = "3"),
                assetId = usdCash.id,
                cashAssetId = usdCash.id,
                cashCurrency = USD.code,
                trnType = TrnType.DEPOSIT,
                quantity = BigDecimal("999"),
                tradeAmount = BigDecimal("999"),
                price = BigDecimal.ONE,
                tradeDate = dateUtils.getFormattedDate("2024-03-03"),
                status = TrnStatus.SETTLED
            )
        val proposedBuy =
            TrnInput(
                CallerRef(batch = "PC", callerId = "4"),
                assetId = equity.id,
                cashAssetId = usdCash.id,
                cashCurrency = USD.code,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = BigDecimal("150.00"),
                tradeAmount = BigDecimal("1500.00"),
                tradeDate = dateUtils.getFormattedDate("2024-03-04"),
                status = TrnStatus.PROPOSED
            )
        val futureDate = dateUtils.date.plusDays(8)
        val futureDeposit =
            TrnInput(
                CallerRef(batch = "PC", callerId = "5"),
                assetId = usdCash.id,
                cashAssetId = usdCash.id,
                cashCurrency = USD.code,
                trnType = TrnType.DEPOSIT,
                quantity = BigDecimal("50"),
                tradeAmount = BigDecimal("50"),
                price = BigDecimal.ONE,
                tradeDate = futureDate,
                status = TrnStatus.PROPOSED
            )

        trnService.save(
            portfolio,
            TrnRequest(
                portfolio.id,
                listOf(proposedDeposit, proposedWithdrawal, settledDeposit, proposedBuy, futureDeposit)
            )
        )

        // Default asAt = today: only the two past PROPOSED cash legs.
        val defaultLegs = proposedCash(portfolio.id, asAt = null)
        assertThat(defaultLegs).hasSize(2)
        assertThat(defaultLegs.map { it.trnType })
            .containsExactlyInAnyOrder(TrnType.DEPOSIT, TrnType.WITHDRAWAL)
        assertThat(defaultLegs).allSatisfy { trn ->
            assertThat(trn.status).isEqualTo(TrnStatus.PROPOSED)
        }

        // asAt covering the future-dated leg includes it (3 legs).
        val futureLegs = proposedCash(portfolio.id, asAt = futureDate.toString())
        assertThat(futureLegs).hasSize(3)
        assertThat(futureLegs).allSatisfy { trn ->
            assertThat(trn.trnType).isIn(TrnType.DEPOSIT, TrnType.WITHDRAWAL)
            assertThat(trn.status).isEqualTo(TrnStatus.PROPOSED)
        }
    }

    private fun proposedCash(
        portfolioId: String,
        asAt: String?
    ) = objectMapper
        .readValue(
            mockMvc
                .perform(
                    get("$TRNS_ROOT/portfolio/$portfolioId/proposed-cash" + (asAt?.let { "?asAt=$it" } ?: ""))
                        .with(
                            SecurityMockMvcRequestPostProcessors.jwt().jwt(
                                mockAuthConfig.getUserToken(Constants.systemUser)
                            )
                        )
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(
                    MockMvcResultMatchers.content().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                ).andReturn()
                .response.contentAsString,
            TrnResponse::class.java
        ).data
        .toTrns()
}