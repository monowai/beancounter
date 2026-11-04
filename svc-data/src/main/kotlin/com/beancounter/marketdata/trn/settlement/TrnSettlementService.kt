package com.beancounter.marketdata.trn.settlement

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnStatusUpdateResponse
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioAccessControl
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.CashTrnServices
import com.beancounter.marketdata.trn.TrnChangeEffects
import com.beancounter.marketdata.trn.TrnPostProcessor
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.getOrThrow
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

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
    private val trnChangeEffects: TrnChangeEffects,
    private val portfolioService: PortfolioService,
    private val portfolioAccessControl: PortfolioAccessControl,
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
        val trns = settled.map { it.trn }
        trnChangeEffects.invalidate(portfolio.id, trns, settled.flatMap { it.cashLegs })
        return trnPostProcessor.postProcess(trns)
    }

    /**
     * Settle a single PROPOSED transaction. Returns it with the cash legs it moved, or null
     * (with a warning) when it is missing, not PROPOSED, future-dated, or FX
     * can't resolve yet (leaving it PROPOSED for retry).
     */
    private fun settleOne(
        portfolio: Portfolio,
        trnId: String
    ): Settled? {
        val trn = trnRepository.findByPortfolioIdAndId(portfolio.id, trnId).orElse(null)
        return when {
            trn == null -> {
                log.warn("Transaction {} not found in portfolio {}", trnId, portfolio.code)
                null
            }
            trn.status != TrnStatus.PROPOSED -> {
                log.warn("Cannot settle transaction {} - status is {} not PROPOSED", trnId, trn.status)
                null
            }
            trn.tradeDate.isAfter(dateUtils.date) -> {
                log.warn("Cannot settle transaction {} - tradeDate {} is in the future", trnId, trn.tradeDate)
                null
            }
            // Shared settle core: FX + status flip + cash emit. Returns null when FX
            // can't resolve yet (leaves it PROPOSED for retry).
            else -> {
                settleWithCash(portfolio, trn)?.also {
                    log.debug("Settled transaction {} for portfolio {}", trnId, portfolio.code)
                }
            }
        }
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
        trnChangeEffects.invalidate(parent.portfolio.id, listOf(parent), siblings)
        return TrnStatusUpdateResponse(updated = parent, siblings = siblings.map { it.id })
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
        val cashLegs = mutableListOf<Trn>()
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
                    cashLegs += unsettle(trn)
                    unsettled.add(trn)
                }
            }
        }
        log.info("Unsettled {} transactions for portfolio {}", unsettled.size, portfolio.code)
        trnChangeEffects.invalidate(portfolio.id, unsettled, cashLegs)
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
    ): Trn? = settleWithCash(portfolio, trn)?.trn

    private fun settleWithCash(
        portfolio: Portfolio,
        trn: Trn
    ): Settled? {
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
        // Forward-dated trns ingest with no rate, so cashAmount was stored as zero.
        // Now that the rate is resolved, derive it before the cash legs are emitted.
        if (needsCashAmount(trn)) {
            trn.cashAmount = CashTrnServices.cashImpact(trn.trnType, trn.tradeAmount, trn.tradeCashRate)
        }
        trn.status = TrnStatus.SETTLED
        trnRepository.save(trn)
        val cash = trnChangeEffects.syncCash(trn)
        cash.warnings.forEach { log.warn("Auto-settle cash for {}: {}", trn.id, it) }
        return Settled(trn, cash.cashLegs)
    }

    /**
     * Unsettle one trn: revert the auto-emitted cash legs to PROPOSED so status
     * moves in sync with the parent, flip the parent SETTLED→PROPOSED, persist.
     * Returns the affected cash legs. The legs stay linked (not deleted) and
     * re-settle to SETTLED when the parent settles again.
     */
    fun unsettle(trn: Trn): List<Trn> {
        val siblings = trnChangeEffects.cashLegs(trn)
        if (siblings.isNotEmpty()) {
            siblings.forEach { it.status = TrnStatus.PROPOSED }
            trnRepository.saveAll(siblings)
            log.info("Unsettle of {} reverted {} auto-settled cash leg(s) to PROPOSED", trn.id, siblings.size)
        }
        trn.status = TrnStatus.PROPOSED
        trnRepository.save(trn)
        return siblings
    }

    /** A cash-impacting trn with a cash asset and a trade amount, but no cash amount yet. */
    private fun needsCashAmount(trn: Trn): Boolean {
        if (!TrnType.isCashImpacted(trn.trnType) || trn.cashAsset == null) return false
        return trn.cashAmount.compareTo(BigDecimal.ZERO) == 0 &&
            trn.tradeAmount.compareTo(BigDecimal.ZERO) != 0
    }
}

/** A settled trn and the cash legs its settle wrote or removed. */
private data class Settled(
    val trn: Trn,
    val cashLegs: List<Trn>
)