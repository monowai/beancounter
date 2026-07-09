package com.beancounter.marketdata.cash

import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.trn.TrnRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * One-off backfill that emits the compensating cash pair for PROPOSED trigger
 * trades which predate leg-status mirroring and therefore carry no BC-AUTO legs.
 *
 * Reuses [CashAutoSettleService.emitCompensatingTransfer], which resolves the
 * funding portfolio, direction, amount and currency and no-ops when the trade
 * isn't fundable or has a zero cash amount — so a proposed dividend with no cash
 * yet is correctly skipped (no phantom zero-value legs).
 *
 * Idempotent: once a trade has BC-AUTO legs it drops out of the candidate query,
 * so re-runs affect nothing. Disabled by default; enable via
 * `beancounter.auto-settle.backfill-proposed-legs=true` for a single deploy to
 * repair existing data, then turn it off.
 */
@Component
@ConditionalOnProperty(
    prefix = "beancounter.auto-settle",
    name = ["backfill-proposed-legs"],
    havingValue = "true"
)
class AutoSettleBackfill(
    private val trnRepository: TrnRepository,
    private val cashAutoSettleService: CashAutoSettleService
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(AutoSettleBackfill::class.java)

    override fun run(vararg args: String) {
        val candidates = trnRepository.findProposedMissingAutoSettleLegs(TRIGGER_TYPES)
        log.info("auto_settle_backfill: {} PROPOSED trade(s) missing cash legs", candidates.size)
        var emitted = 0
        for (trade in candidates) {
            val result = cashAutoSettleService.emitCompensatingTransfer(trade)
            if (result.transactions.isNotEmpty()) {
                emitted++
                log.info(
                    "auto_settle_backfill: emitted {} leg(s) for {} {} ({})",
                    result.transactions.size,
                    trade.trnType,
                    trade.asset.code,
                    trade.portfolio.code
                )
            }
            result.warnings.forEach { log.warn("auto_settle_backfill {}: {}", trade.id, it) }
        }
        log.info("auto_settle_backfill: complete — emitted legs for {}/{} trade(s)", emitted, candidates.size)
    }

    companion object {
        private val TRIGGER_TYPES =
            listOf(
                TrnType.BUY,
                TrnType.SELL,
                TrnType.DIVI,
                TrnType.INCOME,
                TrnType.EXPENSE,
                TrnType.FX_BUY
            )
    }
}