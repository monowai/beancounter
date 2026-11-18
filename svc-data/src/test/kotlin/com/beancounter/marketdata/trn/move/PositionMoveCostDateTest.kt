package com.beancounter.marketdata.trn.move

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PositionMoveRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.enrichment.DefaultEnricher
import com.beancounter.marketdata.assets.enrichment.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.TRNS_ROOT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

/**
 * An ADD's cost date is when the holding was paid for. It must survive the holding being moved
 * to another portfolio, and a cost date after the trade date is refused.
 */
@SpringMvcDbTest
class PositionMoveCostDateTest {
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

    private val tradeDate = LocalDate.parse("2026-06-24")

    private val costDate = LocalDate.parse("2016-11-18")

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
    fun `a moved add keeps its cost date`() {
        val equity = equity()
        val source = bcMvcHelper.portfolio(PortfolioInput(code = "costDateSource", currency = USD.code))
        val target = bcMvcHelper.portfolio(PortfolioInput(code = "costDateTarget", currency = USD.code))
        val add =
            trnService
                .save(source, TrnRequest(source.id, listOf(addInput(equity, costDate))))
                .first()
        assertThat(add.costDate).isEqualTo(costDate)

        mockMvc
            .perform(
                post("$TRNS_ROOT/move")
                    .with(jwt().jwt(bcMvcHelper.token))
                    .content(
                        objectMapper.writeValueAsBytes(
                            PositionMoveRequest(
                                sourcePortfolioId = source.id,
                                targetPortfolioId = target.id,
                                assetId = equity.id
                            )
                        )
                    ).contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        val moved = trnRepository.findById(add.id).orElseThrow()
        assertThat(moved.portfolio.id).isEqualTo(target.id)
        assertThat(moved.costDate).isEqualTo(costDate)
        assertThat(moved.tradeDate).isEqualTo(tradeDate)
    }

    @Test
    fun `a cost date after the trade date is a bad request`() {
        val equity = equity()
        val portfolio = bcMvcHelper.portfolio(PortfolioInput(code = "costDateLater", currency = USD.code))

        mockMvc
            .perform(
                post(TRNS_ROOT)
                    .with(jwt().jwt(bcMvcHelper.token))
                    .content(
                        objectMapper.writeValueAsBytes(
                            TrnRequest(portfolio.id, listOf(addInput(equity, tradeDate.plusDays(1))))
                        )
                    ).contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Cost date cannot be after the trade date"))
    }

    private fun equity(): Asset =
        assetService
            .handle(AssetRequest(AssetInput(NYSE.code, MSFT.code), MSFT.code))
            .data[MSFT.code]!!

    private fun addInput(
        equity: Asset,
        costDate: LocalDate
    ) = TrnInput(
        callerRef = CallerRef(),
        assetId = equity.id,
        trnType = TrnType.ADD,
        tradeDate = tradeDate,
        costDate = costDate,
        quantity = BigDecimal("100"),
        price = BigDecimal.ONE,
        tradeAmount = BigDecimal("100"),
        status = TrnStatus.SETTLED
    )
}