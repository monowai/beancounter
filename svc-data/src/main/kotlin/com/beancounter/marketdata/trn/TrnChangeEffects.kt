package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.cash.CashAutoSettleService
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * The step that follows every trn write: re-sync the compensating cash transfer
 * to the funding portfolio, then invalidate the portfolio's performance cache
 * from the earliest changed trade date. Runs in the caller's transaction.
 */
@Service
class TrnChangeEffects(
    private val cashAutoSettleService: CashAutoSettleService,
    private val cacheInvalidationProducer: CacheInvalidationProducer
) {
    /**
     * Re-sync cash for each trn, in order, then invalidate once from the earliest
     * trade date. Returns the non-fatal auto-settle warnings.
     */
    fun afterChange(
        portfolioId: String,
        trns: Collection<Trn>
    ): List<String> {
        val warnings = trns.flatMap { syncCash(it) }
        invalidate(portfolioId, trns)
        return warnings
    }

    /**
     * Emit, reconcile or revert the trn's compensating cash legs so they mirror its
     * current values and status. A no-op for types that do not move cash.
     */
    fun syncCash(trn: Trn): List<String> = cashAutoSettleService.emitCompensatingTransfer(trn).warnings

    /** The auto-settled cash legs linked to [trn]. */
    fun cashLegs(trn: Trn): List<Trn> = cashAutoSettleService.findSiblings(trn)

    /** Invalidate from the earliest trade date in [trns]; nothing when empty. */
    fun invalidate(
        portfolioId: String,
        trns: Collection<Trn>
    ) {
        trns.minOfOrNull { it.tradeDate }?.let { invalidate(portfolioId, it) }
    }

    fun invalidate(
        portfolioId: String,
        fromDate: LocalDate
    ) = cacheInvalidationProducer.sendTransactionEvent(portfolioId, fromDate)
}