package com.beancounter.marketdata.cash

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.trn.settlement.cashLegInput
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Service to handle cash transfers between cash assets.
 *
 * A cash transfer creates two transactions:
 * - WITHDRAWAL from the source asset
 * - DEPOSIT to the target asset
 *
 * Both assets must be denominated in the same currency.
 * Transfers can be within the same portfolio or across portfolios.
 */
@Service
class CashTransferService(
    private val assetService: AssetService,
    private val portfolioService: PortfolioService,
    private val trnService: TrnService,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val log = LoggerFactory.getLogger(CashTransferService::class.java)

    /**
     * Execute a cash transfer between two cash assets.
     *
     * @param request The transfer request containing source, target, and amount
     * @return Response containing the created transactions
     * @throws IllegalArgumentException if currencies don't match or amount is invalid
     */
    fun transfer(request: CashTransferRequest): CashTransferResponse {
        validateAmounts(request)

        val fromAsset = assetService.find(request.fromAssetId)
        val toAsset = assetService.find(request.toAssetId)
        val fromPortfolio = portfolioService.find(request.fromPortfolioId)
        val toPortfolio = portfolioService.find(request.toPortfolioId)

        val fromCurrency = getCashAssetCurrency(fromAsset)
        val toCurrency = getCashAssetCurrency(toAsset)
        require(fromCurrency == toCurrency) {
            "Cannot transfer between different currencies: $fromCurrency and $toCurrency"
        }

        val tradeDate = request.tradeDate ?: dateUtils.date

        // WITHDRAWAL leg: amount sent from source. cashAssetId lets the cash ladder track it.
        val withdrawalTrns =
            saveLeg(
                fromPortfolio,
                cashLegInput(
                    cashAssetId = fromAsset.id,
                    trnType = TrnType.WITHDRAWAL,
                    amount = request.sentAmount,
                    currency = fromCurrency,
                    tradeDate = tradeDate,
                    status = TrnStatus.SETTLED,
                    comments = request.description ?: "Transfer to ${toAsset.code}"
                )
            )

        // DEPOSIT leg: amount received at destination. cashAssetId lets the cash ladder track it.
        val depositTrns =
            saveLeg(
                toPortfolio,
                cashLegInput(
                    cashAssetId = toAsset.id,
                    trnType = TrnType.DEPOSIT,
                    amount = request.receivedAmount,
                    currency = toCurrency,
                    tradeDate = tradeDate,
                    status = TrnStatus.SETTLED,
                    comments = request.description ?: "Transfer from ${fromAsset.code}"
                )
            )

        log.info(
            "Cash transfer completed: sent {} {}, received {} {} (fee: {}), from {} ({}) to {} ({})",
            request.sentAmount,
            fromCurrency,
            request.receivedAmount,
            toCurrency,
            request.sentAmount - request.receivedAmount,
            fromAsset.code,
            fromPortfolio.code,
            toAsset.code,
            toPortfolio.code
        )

        return CashTransferResponse(withdrawalTrns + depositTrns)
    }

    private fun validateAmounts(request: CashTransferRequest) {
        require(request.sentAmount > BigDecimal.ZERO) {
            "Sent amount must be positive"
        }
        require(request.receivedAmount > BigDecimal.ZERO) {
            "Received amount must be positive"
        }
        require(request.receivedAmount <= request.sentAmount) {
            "Received amount cannot exceed sent amount"
        }
    }

    private fun saveLeg(
        portfolio: Portfolio,
        input: TrnInput
    ): Collection<Trn> = trnService.save(portfolio, TrnRequest(portfolio.id, listOf(input)))

    private fun getCashAssetCurrency(asset: com.beancounter.common.model.Asset): String =
        asset.accountingType?.currency?.code ?: asset.market.currency.code
}