package com.beancounter.marketdata.trn

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.cash.AutoSettleResult
import com.beancounter.marketdata.cash.CashAutoSettleService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The after-change step shared by trn writes and settlement: cash legs first,
 * then one perf-cache invalidation from the earliest changed trade date.
 */
class TrnChangeEffectsTest {
    private val cashAutoSettleService: CashAutoSettleService = mock()
    private val cacheInvalidationProducer: CacheInvalidationProducer = mock()
    private val effects = TrnChangeEffects(cashAutoSettleService, cacheInvalidationProducer)

    private val portfolio =
        Portfolio(
            id = "pf-1",
            code = "TEST",
            name = "Test",
            currency = USD,
            base = USD,
            owner = SystemUser("u", "u@example.com")
        )

    private fun trn(
        id: String,
        tradeDate: LocalDate
    ) = Trn(
        id = id,
        trnType = TrnType.BUY,
        asset = MSFT,
        quantity = BigDecimal.ONE,
        portfolio = portfolio,
        tradeDate = tradeDate
    )

    @Test
    fun `should sync cash for each trn then invalidate once from the earliest trade date`() {
        val later = trn("later", LocalDate.of(2026, 3, 2))
        val earlier = trn("earlier", LocalDate.of(2026, 3, 1))
        whenever(cashAutoSettleService.emitCompensatingTransfer(later))
            .thenReturn(AutoSettleResult(warnings = listOf("later warning")))
        whenever(cashAutoSettleService.emitCompensatingTransfer(earlier))
            .thenReturn(AutoSettleResult(warnings = listOf("earlier warning")))

        val warnings = effects.afterChange(portfolio.id, listOf(later, earlier))

        assertThat(warnings).containsExactly("later warning", "earlier warning")
        inOrder(cashAutoSettleService, cacheInvalidationProducer) {
            verify(cashAutoSettleService).emitCompensatingTransfer(later)
            verify(cashAutoSettleService).emitCompensatingTransfer(earlier)
            verify(cacheInvalidationProducer).sendTransactionEvent(portfolio.id, earlier.tradeDate)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `should not invalidate when no trn changed`() {
        assertThat(effects.afterChange(portfolio.id, emptyList())).isEmpty()

        verify(cacheInvalidationProducer, never()).sendTransactionEvent(any(), any())
    }
}