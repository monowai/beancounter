package com.beancounter.marketdata.trn.move

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.PositionMoveRequest
import com.beancounter.common.contracts.PositionMoveResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.trn.settlement.cashLegInput
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
@Transactional
class PositionMoveService(
    private val trnRepository: TrnRepository,
    private val portfolioService: PortfolioService,
    private val fxTransactions: FxTransactions,
    private val trnService: TrnService,
    private val cacheInvalidationProducer: CacheInvalidationProducer,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val log = LoggerFactory.getLogger(PositionMoveService::class.java)

    fun movePosition(request: PositionMoveRequest): PositionMoveResponse {
        val sourcePortfolio = portfolioService.find(request.sourcePortfolioId)
        val targetPortfolio = portfolioService.find(request.targetPortfolioId)

        val transactions =
            trnRepository
                .findByPortfolioIdAndAssetId(
                    sourcePortfolio.id,
                    request.assetId
                ).toMutableList()

        if (transactions.isEmpty()) {
            throw NotFoundException(
                "No transactions found for asset ${request.assetId} in portfolio ${sourcePortfolio.code}"
            )
        }

        var compensatingCount = 0

        if (request.maintainCashBalances) {
            compensatingCount =
                createCompensatingTransactions(
                    transactions,
                    sourcePortfolio,
                    targetPortfolio
                )
        }

        val needsFxRecalc =
            sourcePortfolio.base.code != targetPortfolio.base.code ||
                sourcePortfolio.currency.code != targetPortfolio.currency.code

        for (trn in transactions) {
            if (needsFxRecalc) {
                recalculateFxRates(trn, targetPortfolio)
            }
            trn.portfolio = targetPortfolio
        }

        trnRepository.saveAll(transactions)

        val earliestDate = transactions.minOfOrNull { it.tradeDate } ?: dateUtils.date
        cacheInvalidationProducer.sendTransactionEvent(sourcePortfolio.id, earliestDate)
        cacheInvalidationProducer.sendTransactionEvent(targetPortfolio.id, earliestDate)

        log.info(
            "Moved {} transactions for asset {} from portfolio {} to {}. Compensating transactions: {}",
            transactions.size,
            request.assetId,
            sourcePortfolio.code,
            targetPortfolio.code,
            compensatingCount
        )

        return PositionMoveResponse(
            movedCount = transactions.size,
            compensatingTransactions = compensatingCount
        )
    }

    /**
     * Net cash amount per cash asset id across the cash-impacting [transactions], with the last
     * transaction seen for that cash asset (the source of its code and currency).
     */
    private fun netCashByAsset(transactions: List<Trn>): Map<String, NetCash> {
        val netByCashAsset = mutableMapOf<String, NetCash>()

        transactions.forEach { trn ->
            val cashImpacted = TrnType.isCashImpacted(trn.trnType)
            val cashAsset = trn.cashAsset
            val hasNonZeroCash = trn.cashAmount.compareTo(BigDecimal.ZERO) != 0
            if (cashImpacted && cashAsset != null && hasNonZeroCash) {
                val cashAssetId = cashAsset.id
                val running = netByCashAsset[cashAssetId]?.amount ?: BigDecimal.ZERO
                netByCashAsset[cashAssetId] = NetCash(running.add(trn.cashAmount), trn, cashAsset.code)
                log.debug(
                    "Compensating: {} {} cashAsset={} cashAmount={}",
                    trn.trnType,
                    trn.id,
                    cashAsset.code,
                    trn.cashAmount
                )
            } else {
                log.debug(
                    "Skipping: {} {} cashImpacted={} hasCashAsset={} cashAmount={}",
                    trn.trnType,
                    trn.id,
                    cashImpacted,
                    cashAsset != null,
                    trn.cashAmount
                )
            }
        }
        return netByCashAsset
    }

    /**
     * Create consolidated compensating cash transactions to maintain
     * cash balances in both portfolios. Nets cash amounts per cash asset
     * to produce a single DEPOSIT/WITHDRAWAL pair per cash asset.
     */
    private fun createCompensatingTransactions(
        transactions: List<Trn>,
        sourcePortfolio: Portfolio,
        targetPortfolio: Portfolio
    ): Int {
        val sourceInputs = mutableListOf<TrnInput>()
        val targetInputs = mutableListOf<TrnInput>()

        netCashByAsset(transactions)
            .filter { (_, net) -> net.amount.compareTo(BigDecimal.ZERO) != 0 }
            .forEach { (cashAssetId, net) ->
                val (netAmount, trn, cashAssetCode) = net
                val comment =
                    "Position move: ${trn.asset.code} from ${sourcePortfolio.code} to ${targetPortfolio.code}"
                val tradeCurrency = trn.cashCurrency?.code ?: trn.tradeCurrency.code

                // Moving a transaction removes its cash impact from source and adds it to target.
                // Net < 0 means debits moved out → source cash rises → WITHDRAW to compensate.
                // Net > 0 means credits moved out → source cash falls → DEPOSIT to compensate.
                val (sourceType, targetType) =
                    if (netAmount < BigDecimal.ZERO) {
                        TrnType.WITHDRAWAL to TrnType.DEPOSIT
                    } else {
                        TrnType.DEPOSIT to TrnType.WITHDRAWAL
                    }
                log.info(
                    "Compensating cashAsset={}: net={} → source={} target={} amount={}",
                    cashAssetCode,
                    netAmount,
                    sourceType,
                    targetType,
                    netAmount.abs()
                )
                sourceInputs.add(
                    createCashInput(cashAssetId, sourceType, netAmount.abs(), tradeCurrency, comment)
                )
                targetInputs.add(
                    createCashInput(cashAssetId, targetType, netAmount.abs(), tradeCurrency, comment)
                )
            }

        var count = 0
        if (sourceInputs.isNotEmpty()) {
            trnService.save(
                sourcePortfolio,
                TrnRequest(sourcePortfolio.id, sourceInputs)
            )
            count += sourceInputs.size
        }
        if (targetInputs.isNotEmpty()) {
            trnService.save(
                targetPortfolio,
                TrnRequest(targetPortfolio.id, targetInputs)
            )
            count += targetInputs.size
        }
        return count
    }

    private fun createCashInput(
        cashAssetId: String,
        trnType: TrnType,
        amount: BigDecimal,
        tradeCurrency: String,
        comment: String
    ): TrnInput =
        cashLegInput(
            cashAssetId = cashAssetId,
            trnType = trnType,
            amount = amount,
            currency = tradeCurrency,
            tradeDate = dateUtils.date,
            status = TrnStatus.SETTLED,
            comments = comment,
            // Preserve historical behaviour: this call site never set price, so
            // persisted trns got TrnInput's own default (ZERO), not the
            // factory's ONE default used by CashAutoSettleService/CashTransferService.
            price = BigDecimal.ZERO
        )

    private fun recalculateFxRates(
        trn: Trn,
        targetPortfolio: Portfolio
    ) {
        val trnInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = trn.asset.id,
                trnType = trn.trnType,
                tradeCurrency = trn.tradeCurrency.code,
                cashCurrency = trn.cashCurrency?.code,
                tradeDate = trn.tradeDate,
                tradeBaseRate = BigDecimal.ZERO,
                tradePortfolioRate = BigDecimal.ZERO,
                tradeCashRate = BigDecimal.ZERO
            )

        fxTransactions.setRates(targetPortfolio, trnInput)

        trn.tradeBaseRate = trnInput.tradeBaseRate
        trn.tradePortfolioRate = trnInput.tradePortfolioRate
        trn.tradeCashRate = trnInput.tradeCashRate
    }
}

/** Netted cash [amount] for one cash asset, and the last [trn] that contributed to it. */
private data class NetCash(
    val amount: BigDecimal,
    val trn: Trn,
    val cashAssetCode: String
)