package com.beancounter.marketdata.trn.settlement

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnChangeEffects
import com.beancounter.marketdata.trn.TrnPostProcessor
import com.beancounter.marketdata.trn.TrnRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

/**
 * Focused unit tests for TrnSettlementService.settleTransactions covering settlement
 * preconditions (forward-dated trns must not flip to SETTLED) and FX resolution on settle.
 */
class TrnSettleTest {
    private lateinit var trnRepository: TrnRepository
    private lateinit var portfolioService: PortfolioService
    private lateinit var cacheInvalidationProducer: CacheInvalidationProducer
    private lateinit var fxTransactions: FxTransactions
    private lateinit var cashAutoSettleService: com.beancounter.marketdata.cash.CashAutoSettleService
    private lateinit var trnPostProcessor: TrnPostProcessor
    private lateinit var trnSettlementService: TrnSettlementService

    private val today: LocalDate = LocalDate.of(2026, 6, 6)

    private val owner = SystemUser("test-user", "test@example.com")
    private val portfolio =
        Portfolio(
            id = "pf-1",
            code = "TEST",
            name = "Test Portfolio",
            currency = USD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun setUp() {
        trnRepository = mock()
        portfolioService = mock()
        cacheInvalidationProducer = mock()
        fxTransactions = mock()
        whenever(portfolioService.find("pf-1")).thenReturn(portfolio)
        cashAutoSettleService = mock()
        whenever(cashAutoSettleService.emitCompensatingTransfer(any()))
            .thenReturn(
                com.beancounter.marketdata.cash
                    .AutoSettleResult()
            )
        trnPostProcessor = mock()
        whenever(trnPostProcessor.postProcess(any<List<Trn>>()))
            .thenAnswer { it.getArgument<List<Trn>>(0) }
        trnSettlementService =
            TrnSettlementService(
                trnRepository = trnRepository,
                fxTransactions = fxTransactions,
                trnChangeEffects = TrnChangeEffects(cashAutoSettleService, cacheInvalidationProducer),
                portfolioService = portfolioService,
                portfolioAccessControl = mock(),
                trnPostProcessor = trnPostProcessor
            )
    }

    @Test
    fun `refuses to settle a trn whose tradeDate is in the future`() {
        val forwardTrn = proposed("trn-future", LocalDate.now(DateUtils.EARLIEST_ZONE).plusDays(5))
        whenever(trnRepository.findByPortfolioIdAndId(portfolio.id, "trn-future"))
            .thenReturn(Optional.of(forwardTrn))

        val result = trnSettlementService.settleTransactions("pf-1", listOf("trn-future"))

        assertThat(result).isEmpty()
        assertThat(forwardTrn.status).isEqualTo(TrnStatus.PROPOSED)
        verify(trnRepository, never()).save(forwardTrn)
        verify(fxTransactions, never()).setRates(any<Portfolio>(), any<Trn>())
    }

    // An exchange settles on its own clock, whatever the service's zone.
    @Test
    fun `settles a trn dated today on its market`() {
        val marketToday = LocalDate.now(ZoneId.of(MSFT.market.timezoneId))
        val dueTrn = proposed("trn-ahead", marketToday)
        whenever(trnRepository.findByPortfolioIdAndId(portfolio.id, "trn-ahead"))
            .thenReturn(Optional.of(dueTrn))

        val result = trnSettlementService.settleTransactions("pf-1", listOf("trn-ahead"))

        assertThat(result).hasSize(1)
        assertThat(dueTrn.status).isEqualTo(TrnStatus.SETTLED)
    }

    // Entered at dawn in Singapore for the US session that evening: dated for the
    // order, it waits until the market's clock reaches that date.
    @Test
    fun `refuses to settle a trn dated after today on its market, though another zone has reached it`() {
        val midway = Market("MIDWAY", timezoneId = "Pacific/Midway")
        val marketTomorrow = LocalDate.now(ZoneId.of(midway.timezoneId)).plusDays(1)
        val early =
            proposed("trn-early", marketTomorrow).apply {
                asset = Asset.of(AssetInput(midway.code, "EARLY"), midway)
            }
        whenever(trnRepository.findByPortfolioIdAndId(portfolio.id, "trn-early"))
            .thenReturn(Optional.of(early))

        val result = trnSettlementService.settleTransactions("pf-1", listOf("trn-early"))

        assertThat(result).isEmpty()
        assertThat(early.status).isEqualTo(TrnStatus.PROPOSED)
    }

    @Test
    fun `settles a trn whose tradeDate is today and resolves FX`() {
        val dueTrn = proposed("trn-today", today)
        whenever(trnRepository.findByPortfolioIdAndId(portfolio.id, "trn-today"))
            .thenReturn(Optional.of(dueTrn))

        val result = trnSettlementService.settleTransactions("pf-1", listOf("trn-today"))

        assertThat(result).hasSize(1)
        assertThat(dueTrn.status).isEqualTo(TrnStatus.SETTLED)
        verify(fxTransactions).setRates(eq(portfolio), eq(dueTrn))
        verify(trnRepository).save(dueTrn)
    }

    @Test
    fun `should set the cash amount when settling a dividend ingested without FX rates`() {
        val divi = proposed("trn-divi", today).apply { cashAsset = usdCashBalance }
        whenever(trnRepository.findByPortfolioIdAndId(portfolio.id, "trn-divi"))
            .thenReturn(Optional.of(divi))
        resolveRateOnSetRates(BigDecimal.ONE)
        val legAmounts = mutableListOf<BigDecimal>()
        whenever(cashAutoSettleService.emitCompensatingTransfer(any())).thenAnswer {
            legAmounts.add(it.getArgument<Trn>(0).cashAmount)
            com.beancounter.marketdata.cash
                .AutoSettleResult()
        }

        trnSettlementService.settleTransactions("pf-1", listOf("trn-divi"))

        assertThat(divi.status).isEqualTo(TrnStatus.SETTLED)
        assertThat(divi.cashAmount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(legAmounts).hasSize(1)
        assertThat(legAmounts.single()).isEqualByComparingTo(BigDecimal("50.00"))
    }

    @Test
    fun `should leave a non-zero cash amount unchanged when settling`() {
        val divi =
            proposed("trn-divi", today).apply {
                cashAsset = usdCashBalance
                cashAmount = BigDecimal("42.00")
            }
        whenever(trnRepository.findByPortfolioIdAndId(portfolio.id, "trn-divi"))
            .thenReturn(Optional.of(divi))
        resolveRateOnSetRates(BigDecimal.ONE)

        trnSettlementService.settleTransactions("pf-1", listOf("trn-divi"))

        assertThat(divi.cashAmount).isEqualByComparingTo(BigDecimal("42.00"))
    }

    private fun resolveRateOnSetRates(rate: BigDecimal) {
        whenever(fxTransactions.setRates(any<Portfolio>(), any<Trn>())).thenAnswer {
            it.getArgument<Trn>(1).tradeCashRate = rate
            Unit
        }
    }

    private fun proposed(
        id: String,
        tradeDate: LocalDate
    ): Trn =
        Trn(
            id = id,
            portfolio = portfolio,
            asset = MSFT,
            trnType = TrnType.DIVI,
            tradeDate = tradeDate,
            quantity = BigDecimal("100"),
            price = BigDecimal("0.50"),
            tradeAmount = BigDecimal("50.00"),
            tradeCurrency = USD,
            status = TrnStatus.PROPOSED
        )
}