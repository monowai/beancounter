package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.cash.AutoSettleResult
import com.beancounter.marketdata.cash.CashAutoSettleService
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * The step that follows every trn write: re-sync the compensating cash transfer
 * to the funding portfolio, then invalidate the performance cache of the trade's
 * portfolio and of each funding portfolio whose cash legs moved. Runs in the
 * caller's transaction.
 */
@Service
class TrnChangeEffects(
    private val cashAutoSettleService: CashAutoSettleService,
    private val cacheInvalidationProducer: CacheInvalidationProducer
) {
    /**
     * Re-sync cash for each trn, in order, then invalidate (see [invalidate]).
     * Returns the non-fatal auto-settle warnings.
     */
    fun afterChange(
        portfolioId: String,
        trns: Collection<Trn>
    ): List<String> {
        val synced = trns.map { syncCash(it) }
        invalidate(portfolioId, trns, synced.flatMap { it.cashLegs })
        return synced.flatMap { it.warnings }
    }

    /**
     * Emit, reconcile or revert the trn's compensating cash legs so they mirror its
     * current values and status. A no-op for types that do not move cash.
     */
    fun syncCash(trn: Trn): AutoSettleResult = cashAutoSettleService.emitCompensatingTransfer(trn)

    /** The auto-settled cash legs linked to [trn]. */
    fun cashLegs(trn: Trn): List<Trn> = cashAutoSettleService.findSiblings(trn)

    /**
     * Invalidate [portfolioId] from the earliest trade date in [trns], then each other
     * portfolio holding one of [cashLegs] from its earliest leg date, once per portfolio.
     * Nothing when [trns] is empty.
     */
    fun invalidate(
        portfolioId: String,
        trns: Collection<Trn>,
        cashLegs: Collection<Trn>
    ) {
        val fromDate = trns.minOfOrNull { it.tradeDate } ?: return
        invalidate(portfolioId, fromDate)
        cashLegs
            .filter { it.portfolio.id != portfolioId }
            .groupBy { it.portfolio.id }
            .forEach { (fundingId, legs) -> invalidate(fundingId, legs.minOf { it.tradeDate }) }
    }

    fun invalidate(
        portfolioId: String,
        fromDate: LocalDate
    ) = cacheInvalidationProducer.sendTransactionEvent(portfolioId, fromDate)
}