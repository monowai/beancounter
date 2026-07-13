package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.cash.AutoSettleResult
import com.beancounter.marketdata.cash.CashAutoSettleService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The shared settle / unsettle core every entry path funnels through. The
 * orchestration tests (TrnSettleTest, AutoSettleServiceTest) mock this service;
 * these tests pin the actual FX + status + cash behaviour once.
 */
class TrnSettlementServiceTest {
    private lateinit var trnRepository: TrnRepository
    private lateinit var fxTransactions: FxTransactions
    private lateinit var cashAutoSettleService: CashAutoSettleService
    private lateinit var service: TrnSettlementService

    private val owner = SystemUser("u", "u@example.com")
    private val portfolio =
        Portfolio(id = "pf-1", code = "TEST", name = "Test", currency = USD, base = USD, owner = owner)

    @BeforeEach
    fun setUp() {
        trnRepository = org.mockito.kotlin.mock()
        fxTransactions = org.mockito.kotlin.mock()
        cashAutoSettleService = org.mockito.kotlin.mock()
        whenever(cashAutoSettleService.emitCompensatingTransfer(any())).thenReturn(AutoSettleResult())
        service =
            TrnSettlementService(
                trnRepository,
                fxTransactions,
                cashAutoSettleService,
                org.mockito.kotlin.mock(),
                org.mockito.kotlin.mock(),
                org.mockito.kotlin.mock(),
                org.mockito.kotlin.mock(),
                org.mockito.kotlin.mock()
            )
    }

    private fun proposed(id: String) =
        Trn(
            id = id,
            portfolio = portfolio,
            asset = MSFT,
            trnType = TrnType.BUY,
            tradeDate = LocalDate.of(2026, 6, 6),
            quantity = BigDecimal("1"),
            price = BigDecimal("10"),
            tradeAmount = BigDecimal("10"),
            tradeCurrency = USD,
            status = TrnStatus.PROPOSED
        )

    @Test
    fun `settle resolves FX, flips to SETTLED, persists and emits the cash transfer`() {
        val trn = proposed("t1")

        val result = service.settle(portfolio, trn)

        assertThat(result).isSameAs(trn)
        assertThat(trn.status).isEqualTo(TrnStatus.SETTLED)
        verify(fxTransactions).setRates(portfolio, trn)
        verify(trnRepository).save(trn)
        verify(cashAutoSettleService).emitCompensatingTransfer(trn)
    }

    @Test
    fun `settle returns null and leaves PROPOSED when FX cannot resolve`() {
        val trn = proposed("t2")
        whenever(fxTransactions.setRates(portfolio, trn)).thenThrow(RuntimeException("no rate"))

        val result = service.settle(portfolio, trn)

        assertThat(result).isNull()
        assertThat(trn.status).isEqualTo(TrnStatus.PROPOSED)
        verify(trnRepository, never()).save(any())
        verify(cashAutoSettleService, never()).emitCompensatingTransfer(any())
    }

    @Test
    fun `unsettle reverts the cash legs to PROPOSED, flips parent and reports ids`() {
        val trn = proposed("t3").apply { status = TrnStatus.SETTLED }
        val legs =
            listOf(
                proposed("w").apply { status = TrnStatus.SETTLED },
                proposed("d").apply { status = TrnStatus.SETTLED }
            )
        whenever(cashAutoSettleService.findSiblings(trn)).thenReturn(legs)

        val removed = service.unsettle(trn)

        assertThat(removed).containsExactly("w", "d")
        assertThat(trn.status).isEqualTo(TrnStatus.PROPOSED)
        // Legs move in sync — reverted to PROPOSED and persisted, not deleted.
        assertThat(legs.map { it.status }).containsOnly(TrnStatus.PROPOSED)
        verify(trnRepository).saveAll(legs)
        verify(trnRepository).save(trn)
    }

    @Test
    fun `unsettle with no cash legs just flips to PROPOSED`() {
        val trn = proposed("t4").apply { status = TrnStatus.SETTLED }
        whenever(cashAutoSettleService.findSiblings(trn)).thenReturn(emptyList())

        val removed = service.unsettle(trn)

        assertThat(removed).isEmpty()
        assertThat(trn.status).isEqualTo(TrnStatus.PROPOSED)
        verify(trnRepository, never()).saveAll(any<List<Trn>>())
        verify(trnRepository).save(trn)
    }
}