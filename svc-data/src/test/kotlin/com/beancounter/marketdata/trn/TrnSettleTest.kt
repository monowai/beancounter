package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.cash.CashAutoSettleService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.portfolio.PortfolioShareRepository
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * Focused unit tests for TrnService.settleTransactions covering settlement preconditions
 * (forward-dated trns must not flip to SETTLED) and FX deferral on settle.
 */
class TrnSettleTest {
    private lateinit var trnRepository: TrnRepository
    private lateinit var portfolioService: PortfolioService
    private lateinit var fxTransactions: FxTransactions
    private lateinit var trnMigrator: TrnMigrator
    private lateinit var cacheInvalidationProducer: CacheInvalidationProducer
    private lateinit var systemUserService: SystemUserService
    private lateinit var trnService: TrnService
    private lateinit var dateUtils: DateUtils

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
        fxTransactions = mock()
        trnMigrator = mock()
        cacheInvalidationProducer = mock()
        systemUserService = mock()
        dateUtils = mock()
        whenever(dateUtils.date).thenReturn(today)
        whenever(portfolioService.find("pf-1")).thenReturn(portfolio)
        whenever(trnMigrator.upgrade(any())).thenAnswer { it.arguments[0] as Trn }
        whenever(systemUserService.getOrThrow()).thenReturn(owner)
        trnService =
            TrnService(
                trnRepository = trnRepository,
                trnInputMapper = mock(),
                portfolioService = portfolioService,
                trnMigrator = trnMigrator,
                assetFinder = mock<AssetFinder>(),
                systemUserService = systemUserService,
                cacheInvalidationProducer = cacheInvalidationProducer,
                portfolioShareRepository = mock<PortfolioShareRepository>(),
                cashAutoSettleService = mock<CashAutoSettleService>(),
                fxTransactions = fxTransactions,
                dateUtils = dateUtils
            )
    }

    @Test
    fun `refuses to settle a trn whose tradeDate is in the future`() {
        val forwardTrn = proposed("trn-future", today.plusDays(5))
        whenever(trnRepository.findByPortfolioIdAndId(portfolio.id, "trn-future"))
            .thenReturn(Optional.of(forwardTrn))

        val result = trnService.settleTransactions("pf-1", listOf("trn-future"))

        assertThat(result).isEmpty()
        assertThat(forwardTrn.status).isEqualTo(TrnStatus.PROPOSED)
        verify(trnRepository, never()).save(forwardTrn)
        verify(fxTransactions, never()).setRates(any(), any<Trn>())
    }

    @Test
    fun `settles a trn whose tradeDate is today and resolves FX`() {
        val dueTrn = proposed("trn-today", today)
        whenever(trnRepository.findByPortfolioIdAndId(portfolio.id, "trn-today"))
            .thenReturn(Optional.of(dueTrn))
        whenever(trnRepository.save(dueTrn)).thenReturn(dueTrn)

        val result = trnService.settleTransactions("pf-1", listOf("trn-today"))

        assertThat(result).hasSize(1)
        assertThat(dueTrn.status).isEqualTo(TrnStatus.SETTLED)
        verify(fxTransactions).setRates(portfolio, dueTrn)
        verify(trnRepository).save(dueTrn)
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