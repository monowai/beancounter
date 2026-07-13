package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnStatusUpdateResponse
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.cash.CashAutoSettleService
import com.beancounter.marketdata.portfolio.PortfolioAccessControl
import com.beancounter.marketdata.portfolio.PortfolioService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Single source of truth for the settle / unsettle state transition and its cash
 * side effects, so every caller behaves identically regardless of the UI or entry
 * path — manual bulk settle ([settleTransactions]), the scheduled event settle
 * ([AutoSettleService]), single and bulk unsettle ([unsettle],
 * [unsettleTransactions]).
 *
 * The per-trn core ([settle] / [unsettle]) owns the transition itself: FX
 * resolution, the status flip + persist, and the compensating cash transfer
 * (reconcile on settle, revert legs to PROPOSED on unsettle). The batch entry points own the
 * surrounding preconditions (future-date guard, auth/view checks, status checks)
 * and cache invalidation.
 */
@Service
@Transactional
class TrnSettlementService(
    private val trnRepository: TrnRepository,
    private val fxTransactions: FxTransactions,
    private val cashAutoSettleService: CashAutoSettleService,
    private val portfolioService: PortfolioService,
    private val portfolioAccessControl: PortfolioAccessControl,
    private val cacheInvalidationProducer: CacheInvalidationProducer,
    private val dateUtils: DateUtils,
    private val trnPostProcessor: TrnPostProcessor
) {
    private val log = LoggerFactory.getLogger(TrnSettlementService::class.java)

    /**
     * Settle transactions by updating their status from PROPOSED to SETTLED.
     */
    fun settleTransactions(
        portfolioId: String,
        trnIds: List<String>
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val settled = trnIds.mapNotNull { settleOne(portfolio, it) }
        log.info("Settled {} transactions for portfolio {}", settled.size, portfolio.code)
        val earliestSettled = settled.minOfOrNull { it.tradeDate }
        if (earliestSettled != null) {
            cacheInvalidationProducer.sendTransactionEvent(portfolio.id, earliestSettled)
        }
        return trnPostProcessor.postProcess(settled)
    }

    /**
     * Settle a single PROPOSED transaction. Returns the settled Trn, or null
     * (with a warning) when it is missing, not PROPOSED, future-dated, or FX
     * can't resolve yet (leaving it PROPOSED for retry).
     */
    private fun settleOne(
        portfolio: Portfolio,
        trnId: String
    ): Trn? {
        val trn =
            trnRepository.findByPortfolioIdAndId(portfolio.id, trnId).orElse(null)
                ?: run {
                    log.warn("Transaction {} not found in portfolio {}", trnId, portfolio.code)
                    return null
                }
        if (trn.status != TrnStatus.PROPOSED) {
            log.warn("Cannot settle transaction {} - status is {} not PROPOSED", trnId, trn.status)
            return null
        }
        if (trn.tradeDate.isAfter(dateUtils.date)) {
            log.warn("Cannot settle transaction {} - tradeDate {} is in the future", trnId, trn.tradeDate)
            return null
        }
        // Shared settle core: FX + status flip + cash emit. Returns null when FX
        // can't resolve yet (leaves it PROPOSED for retry).
        val saved = settle(portfolio, trn) ?: return null
        log.debug("Settled transaction {} for portfolio {}", trnId, portfolio.code)
        return saved
    }

    /**
     * Toggle a trn from SETTLED to PROPOSED, reverting its auto-emitted cash legs
     * to PROPOSED (see [unsettle]). `siblings` reports the affected leg ids for the
     * UI to refresh.
     *
     * Idempotency: calling unsettle on a trn that isn't SETTLED throws — there's
     * no useful "transition to PROPOSED from PROPOSED" semantic.
     */
    fun unsettle(trnId: String): TrnStatusUpdateResponse {
        val parent = trnRepository.getOrThrow(trnId)
        if (!portfolioAccessControl.canView(parent.portfolio)) {
            throw NotFoundException("Transaction not found: $trnId")
        }
        require(parent.status == TrnStatus.SETTLED) {
            "Cannot unsettle trn $trnId: status is ${parent.status}, expected SETTLED"
        }
        val siblings = unsettle(parent)
        cacheInvalidationProducer.sendTransactionEvent(parent.portfolio.id, parent.tradeDate)
        return TrnStatusUpdateResponse(updated = parent, siblings = siblings)
    }

    /**
     * Bulk unsettle for a portfolio — the multi-select counterpart to
     * [settleTransactions]. Each SETTLED trn flows through the same per-trn
     * [unsettle] core (cash-leg cascade) as the single path, so behaviour is
     * identical regardless of UI. Non-SETTLED / missing ids are skipped with a
     * log, never aborting the batch.
     */
    fun unsettleTransactions(
        portfolioId: String,
        trnIds: List<String>
    ): Collection<Trn> {
        val portfolio = portfolioService.find(portfolioId)
        val unsettled = mutableListOf<Trn>()
        for (trnId in trnIds) {
            val trn = trnRepository.findByPortfolioIdAndId(portfolio.id, trnId).orElse(null)
            when {
                trn == null -> {
                    log.warn("Transaction {} not found in portfolio {}", trnId, portfolio.code)
                }
                trn.status != TrnStatus.SETTLED -> {
                    log.warn("Cannot unsettle {} - status is {} not SETTLED", trnId, trn.status)
                }
                else -> {
                    unsettle(trn)
                    unsettled.add(trn)
                }
            }
        }
        log.info("Unsettled {} transactions for portfolio {}", unsettled.size, portfolio.code)
        unsettled.minOfOrNull { it.tradeDate }?.let {
            cacheInvalidationProducer.sendTransactionEvent(portfolio.id, it)
        }
        return trnPostProcessor.postProcess(unsettled)
    }

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
     * Unsettle one trn: revert the auto-emitted cash legs to PROPOSED so status
     * moves in sync with the parent, flip the parent SETTLED→PROPOSED, persist.
     * Returns the affected sibling ids. The legs stay linked (not deleted) and
     * re-settle to SETTLED when the parent settles again.
     */
    fun unsettle(trn: Trn): List<String> {
        val siblings = cashAutoSettleService.findSiblings(trn)
        if (siblings.isNotEmpty()) {
            siblings.forEach { it.status = TrnStatus.PROPOSED }
            trnRepository.saveAll(siblings)
            log.info("Unsettle of {} reverted {} auto-settled cash leg(s) to PROPOSED", trn.id, siblings.size)
        }
        trn.status = TrnStatus.PROPOSED
        trnRepository.save(trn)
        return siblings.map { it.id }
    }
}