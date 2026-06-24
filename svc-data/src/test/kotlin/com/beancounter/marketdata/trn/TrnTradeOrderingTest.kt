package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Same-day transactions must order deterministically. When an onboarding BALANCE
 * and a later-entered ADD share a tradeDate, the trades view (newest-first) must
 * tie-break on createdAt so the order is stable rather than depending on the
 * database's row order. See [TrnRepository.findByPortfolioIdAndAssetIdAndTrnType].
 */
@SpringMvcDbTest
class TrnTradeOrderingTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var trnService: TrnService

    @Autowired
    lateinit var trnRepository: TrnRepository

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxClientService: FxRateService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun setupObjects() {
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                mockAuthConfig.getUserToken(Constants.systemUser)
            )
        bcMvcHelper.registerUser()
        enrichmentFactory.register(defaultEnricher)
        Mockito
            .`when`(
                fxClientService.getRates(any(), any())
            ).thenReturn(FxResponse(FxPairResults()))
    }

    @Test
    fun `same-day trades tie-break on createdAt newest-first`() {
        val equity =
            assetService
                .handle(
                    AssetRequest(AssetInput(NYSE.code, MSFT.code), MSFT.code)
                ).data[MSFT.code]
        assertThat(equity).isNotNull

        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(code = "tradeOrderTest", currency = USD.code)
            )

        val tradeDate = LocalDate.parse("2026-06-24")

        // BALANCE entered first (onboarding), then ADD (payslip) on the same day.
        // Separate save calls so createdAt differs by wall-clock.
        val balance =
            trnService
                .save(
                    portfolio,
                    TrnRequest(
                        portfolio.id,
                        listOf(
                            TrnInput(
                                callerRef = CallerRef(),
                                assetId = equity!!.id,
                                trnType = TrnType.BALANCE,
                                tradeDate = tradeDate,
                                quantity = BigDecimal("273000.00"),
                                price = BigDecimal.ZERO,
                                tradeAmount = BigDecimal("273000.00"),
                                status = TrnStatus.SETTLED
                            )
                        )
                    )
                ).first()

        val add =
            trnService
                .save(
                    portfolio,
                    TrnRequest(
                        portfolio.id,
                        listOf(
                            TrnInput(
                                callerRef = CallerRef(),
                                assetId = equity.id,
                                trnType = TrnType.ADD,
                                tradeDate = tradeDate,
                                quantity = BigDecimal("2312.50"),
                                price = BigDecimal.ONE,
                                tradeAmount = BigDecimal("2312.50"),
                                status = TrnStatus.SETTLED
                            )
                        )
                    )
                ).first()

        // Precondition: ADD was created after BALANCE.
        assertThat(add.createdAt).isAfter(balance.createdAt)

        val trades =
            trnRepository
                .findByPortfolioIdAndAssetIdAndTrnType(
                    portfolio.id,
                    equity.id,
                    listOf(TrnType.BALANCE, TrnType.ADD)
                ).toList()

        assertThat(trades)
            .extracting<TrnType> { it.trnType }
            .containsExactly(TrnType.ADD, TrnType.BALANCE)
    }
}