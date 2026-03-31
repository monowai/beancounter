package com.beancounter.marketdata.trn

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
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.portfolio.PortfolioService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional
class PositionMoveService(
    private val trnRepository: TrnRepository,
    private val portfolioService: PortfolioService,
    private val fxTransactions: FxTransactions,
    private val trnService: TrnService,
    private val cacheInvalidationProducer: CacheInvalidationProducer
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

        val earliestDate = transactions.minOfOrNull { it.tradeDate } ?: LocalDate.now()
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
     * Create consolidated compensating cash transactions to maintain
     * cash balances in both portfolios. Nets cash amounts per cash asset
     * to produce a single DEPOSIT/WITHDRAWAL pair per cash asset.
     */
    private fun createCompensatingTransactions(
        transactions: List<Trn>,
        sourcePortfolio: Portfolio,
        targetPortfolio: Portfolio
    ): Int {
        // Net cash amounts per cash asset ID
        val netByCashAsset = mutableMapOf<String, BigDecimal>()
        val sampleTrnByCashAsset = mutableMapOf<String, Trn>()

        transactions.forEach { trn ->
            val cashImpacted = TrnType.isCashImpacted(trn.trnType)
            val hasCashAsset = trn.cashAsset != null
            val hasNonZeroCash = trn.cashAmount.compareTo(BigDecimal.ZERO) != 0
            if (cashImpacted && hasCashAsset && hasNonZeroCash) {
                val cashAssetId = trn.cashAsset!!.id
                netByCashAsset[cashAssetId] =
                    (netByCashAsset[cashAssetId] ?: BigDecimal.ZERO).add(trn.cashAmount)
                sampleTrnByCashAsset[cashAssetId] = trn
                log.debug(
                    "Compensating: {} {} cashAsset={} cashAmount={}",
                    trn.trnType,
                    trn.id,
                    trn.cashAsset!!.code,
                    trn.cashAmount
                )
            } else {
                log.debug(
                    "Skipping: {} {} cashImpacted={} hasCashAsset={} cashAmount={}",
                    trn.trnType,
                    trn.id,
                    cashImpacted,
                    hasCashAsset,
                    trn.cashAmount
                )
            }
        }

        val sourceInputs = mutableListOf<TrnInput>()
        val targetInputs = mutableListOf<TrnInput>()

        netByCashAsset
            .filter { (_, netAmount) -> netAmount.compareTo(BigDecimal.ZERO) != 0 }
            .forEach { (cashAssetId, netAmount) ->
                val trn = sampleTrnByCashAsset[cashAssetId]!!
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
                    trn.cashAsset!!.code,
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
        TrnInput(
            callerRef = CallerRef(),
            assetId = cashAssetId,
            cashAssetId = cashAssetId,
            trnType = trnType,
            tradeAmount = amount,
            tradeCurrency = tradeCurrency,
            cashCurrency = tradeCurrency,
            tradeDate = LocalDate.now(),
            status = TrnStatus.SETTLED,
            comments = comment
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