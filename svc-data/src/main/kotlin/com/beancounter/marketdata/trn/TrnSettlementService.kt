package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.marketdata.cash.CashAutoSettleService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Single source of truth for the settle / unsettle state transition and its cash
 * side effects, so every caller behaves identically regardless of the UI or entry
 * path — manual bulk settle ([TrnService.settleTransactions]), the scheduled event
 * settle ([AutoSettleService]), single and bulk unsettle ([TrnService.unsettle],
 * [TrnService.unsettleTransactions]).
 *
 * Callers own their own preconditions (future-date guard, owner opt-in, auth,
 * status checks); this service owns the transition itself: FX resolution, the
 * status flip + persist, and the compensating cash transfer (emit on settle,
 * cascade-delete on unsettle).
 */
@Service
class TrnSettlementService(
    private val trnRepository: TrnRepository,
    private val fxTransactions: FxTransactions,
    private val cashAutoSettleService: CashAutoSettleService
) {
    private val log = LoggerFactory.getLogger(TrnSettlementService::class.java)

    /**
     * Settle one trn: resolve FX, flip PROPOSED→SETTLED, persist, and emit the
     * compensating cash transfer. Returns the settled trn, or null when FX can't
     * yet be resolved (forward-dated events) — the caller leaves it PROPOSED for
     * retry.
     */
    fun settle(
        portfolio: Portfolio,
        trn: Trn
    ): Trn? {
        try {
            // Resolve FX now that settlement is confirmed. Ingest of forward-dated
            // event trns (DIVI payDate) defers FX; providers reject future dates.
            fxTransactions.setRates(portfolio, trn)
        } catch (
            // FX providers throw a range of runtime errors (rate lookup, parse,
            // future-date rejection); any of them means "can't settle yet".
            @Suppress("TooGenericExceptionCaught")
            ex: RuntimeException
        ) {
            log.warn(
                "FX resolution failed for {} on {} — leaving PROPOSED for retry: {}",
                trn.id,
                trn.tradeDate,
                ex.message
            )
            return null
        }
        trn.status = TrnStatus.SETTLED
        trnRepository.save(trn)
        cashAutoSettleService
            .emitCompensatingTransfer(trn)
            .warnings
            .forEach { log.warn("Auto-settle cash for {}: {}", trn.id, it) }
        return trn
    }

    /**
     * Unsettle one trn: cascade-delete the auto-emitted cash legs, flip
     * SETTLED→PROPOSED, persist. Returns the removed sibling ids. The cash legs
     * are re-emitted if the trn settles again.
     */
    fun unsettle(trn: Trn): List<String> {
        val siblings = cashAutoSettleService.findSiblings(trn)
        if (siblings.isNotEmpty()) {
            trnRepository.deleteAll(siblings)
            log.info("Unsettle of {} removed {} auto-settled cash leg(s)", trn.id, siblings.size)
        }
        trn.status = TrnStatus.PROPOSED
        trnRepository.save(trn)
        return siblings.map { it.id }
    }
}