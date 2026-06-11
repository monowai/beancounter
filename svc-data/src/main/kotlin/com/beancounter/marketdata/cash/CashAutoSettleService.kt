package com.beancounter.marketdata.cash

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnInputMapper
import com.beancounter.marketdata.trn.TrnRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Emits a compensating cash transfer (WITHDRAWAL + DEPOSIT) when a
 * cash-impacting trade is saved against a portfolio that points (directly or
 * via its owner's default) at a funding portfolio.
 *
 * Resolution: trade.portfolio.cashPortfolioId overrides
 * owner.cashPortfolioId. null on both disables auto-settle.
 *
 * Direction:
 *   debit  types (BUY / EXPENSE / FX_BUY)   — W from master,        D into trade.portfolio
 *   credit types (SELL / DIVI / INCOME)     — W from trade.portfolio, D into master
 *
 * Grouping: emitted pair stamps `provider = BC-AUTO`,
 *           `batch = parent.callerRef.callerId`. The unsettle / delete UI
 *           uses this to find and prompt the user to remove the cash legs.
 *
 * Skip conditions (no transactions emitted; warnings may be returned):
 *   - non-trigger trnType
 *   - cashAsset null or cashAmount == 0
 *   - no funding portfolio resolved
 *   - master == trade.portfolio
 *   - debit case AND master has never held this cash asset (block + warn;
 *     trade still succeeds with negative balance in invest portfolio)
 */
@Service
class CashAutoSettleService(
    private val portfolioService: PortfolioService,
    private val trnInputMapper: TrnInputMapper,
    private val trnRepository: TrnRepository,
    private val keyGenUtils: KeyGenUtils
) {
    private val log = LoggerFactory.getLogger(CashAutoSettleService::class.java)

    fun emitCompensatingTransfer(trade: Trn): AutoSettleResult {
        if (trade.trnType !in triggerTypes) return AutoSettleResult()
        val cashAsset = trade.cashAsset ?: return AutoSettleResult()
        if (trade.cashAmount.signum() == 0) return AutoSettleResult()

        val masterId =
            trade.portfolio.cashPortfolioId
                ?: trade.portfolio.owner.cashPortfolioId
                ?: return AutoSettleResult()
        if (masterId == trade.portfolio.id) return AutoSettleResult()

        // findOrNull (not find) — stream-consumer threads have no JWT, and
        // `find` calls `canView` which throws via `systemUserService.getOrThrow()`.
        // That exception used to be swallowed by `runCatching`, but the outer
        // `TrnService.save` `@Transactional` had already been marked
        // rollback-only, so the commit blew up with UnexpectedRollbackException
        // (DATA-4Z). The auto-settle path is system-internal — owner-scope
        // is enforced upstream when the trade itself is saved.
        val master =
            portfolioService.findOrNull(masterId)
                ?: return AutoSettleResult(
                    warnings = listOf("Cash portfolio $masterId not found; auto-settle skipped")
                )

        val isDebit = trade.trnType in cashDebitTypes
        val ccy = trade.cashCurrency?.code ?: cashAsset.market.currency.code

        if (isDebit && !masterHasCashHistory(master.id, cashAsset.id)) {
            val msg =
                "Cash portfolio ${master.code} has no $ccy balance; " +
                    "deposit $ccy first to enable auto-settle"
            log.info("auto_settle_skipped: trade={} reason=no_cash_history", trade.id)
            return AutoSettleResult(warnings = listOf(msg))
        }

        val groupBatch =
            trade.callerRef?.callerId
                ?: return AutoSettleResult(
                    warnings = listOf("Trade ${trade.id} missing callerId; auto-settle skipped")
                )
        val amount = trade.cashAmount.abs()
        val tradeDate = trade.settleDate ?: trade.tradeDate

        val fromPortfolio = if (isDebit) master else trade.portfolio
        val toPortfolio = if (isDebit) trade.portfolio else master

        val withdrawalInput =
            TrnInput(
                callerRef = autoCallerRef(groupBatch),
                assetId = cashAsset.id,
                cashAssetId = cashAsset.id,
                trnType = TrnType.WITHDRAWAL,
                tradeAmount = amount,
                tradeCurrency = ccy,
                cashCurrency = ccy,
                price = BigDecimal.ONE,
                tradeDate = tradeDate,
                status = TrnStatus.SETTLED,
                comments = "Auto-settle for ${trade.trnType} ${trade.asset.code}"
            )
        val depositInput =
            TrnInput(
                callerRef = autoCallerRef(groupBatch),
                assetId = cashAsset.id,
                cashAssetId = cashAsset.id,
                trnType = TrnType.DEPOSIT,
                tradeAmount = amount,
                tradeCurrency = ccy,
                cashCurrency = ccy,
                price = BigDecimal.ONE,
                tradeDate = tradeDate,
                status = TrnStatus.SETTLED,
                comments = "Auto-settle for ${trade.trnType} ${trade.asset.code}"
            )

        val withdrawal =
            trnInputMapper
                .convert(fromPortfolio, TrnRequest(fromPortfolio.id, listOf(withdrawalInput)))
                .single()
        val deposit =
            trnInputMapper
                .convert(toPortfolio, TrnRequest(toPortfolio.id, listOf(depositInput)))
                .single()

        val saved = trnRepository.saveAll(listOf(withdrawal, deposit)).toList()
        log.debug(
            "auto_settle_emitted: trade={} pair=[{},{}] group={}",
            trade.id,
            saved[0].id,
            saved[1].id,
            groupBatch
        )
        return AutoSettleResult(transactions = saved)
    }

    /**
     * Returns trns where provider=BC-AUTO and batch=parent.callerRef.callerId —
     * the W+D pair emitted by an earlier auto-settle for this parent trade.
     */
    fun findSiblings(parent: Trn): List<Trn> {
        val parentCallerId = parent.callerRef?.callerId ?: return emptyList()
        return trnRepository.findByCallerRefProviderAndCallerRefBatch(
            AUTO_PROVIDER,
            parentCallerId
        )
    }

    private fun autoCallerRef(batch: String) =
        CallerRef(
            provider = AUTO_PROVIDER,
            batch = batch,
            callerId = keyGenUtils.id
        )

    // Uses the cash-ladder query, which matches when t.cashAsset.id == cashAssetId
    // (covers DEPOSIT/WITHDRAWAL/BUY/SELL/DIVI/INCOME/EXPENSE) OR
    // (t.asset.id == cashAssetId AND trnType == FX_BUY). Returning isNotEmpty()
    // means "master has ever moved this currency through it".
    //
    // NOTE: presence != positive balance. A precise balance check (sum signed
    // cashAmount > 0) is a follow-up; for now any history unblocks auto-settle.
    private fun masterHasCashHistory(
        portfolioId: String,
        cashAssetId: String
    ): Boolean =
        trnRepository
            .findByPortfolioIdAndCashAssetId(
                portfolioId,
                cashAssetId,
                LocalDate.now(),
                TrnStatus.SETTLED
            ).isNotEmpty()

    companion object {
        const val AUTO_PROVIDER = "BC-AUTO"

        private val triggerTypes =
            setOf(
                TrnType.BUY,
                TrnType.SELL,
                TrnType.DIVI,
                TrnType.INCOME,
                TrnType.EXPENSE,
                TrnType.FX_BUY
            )
        private val cashDebitTypes =
            setOf(TrnType.BUY, TrnType.EXPENSE, TrnType.FX_BUY)
    }
}

data class AutoSettleResult(
    val transactions: List<Trn> = emptyList(),
    val warnings: List<String> = emptyList()
)