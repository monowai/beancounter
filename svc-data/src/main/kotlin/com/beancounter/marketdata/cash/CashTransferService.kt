package com.beancounter.marketdata.cash

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnService
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
    private val trnService: TrnService
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
        // Validate amounts
        require(request.sentAmount > BigDecimal.ZERO) {
            "Sent amount must be positive"
        }
        require(request.receivedAmount > BigDecimal.ZERO) {
            "Received amount must be positive"
        }
        require(request.receivedAmount <= request.sentAmount) {
            "Received amount cannot exceed sent amount"
        }

        // Load assets and portfolios
        val fromAsset = assetService.find(request.fromAssetId)
        val toAsset = assetService.find(request.toAssetId)
        val fromPortfolio = portfolioService.find(request.fromPortfolioId)
        val toPortfolio = portfolioService.find(request.toPortfolioId)

        // Validate same currency
        val fromCurrency = getCashAssetCurrency(fromAsset)
        val toCurrency = getCashAssetCurrency(toAsset)
        require(fromCurrency == toCurrency) {
            "Cannot transfer between different currencies: $fromCurrency and $toCurrency"
        }

        val transactions = mutableListOf<Trn>()

        // Create WITHDRAWAL transaction (amount sent from source)
        // Set cashAssetId = fromAsset.id so the cash ladder can track this transaction
        val withdrawalInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = fromAsset.id,
                cashAssetId = fromAsset.id,
                trnType = TrnType.WITHDRAWAL,
                tradeAmount = request.sentAmount,
                tradeCurrency = fromCurrency,
                cashCurrency = fromCurrency,
                price = BigDecimal.ONE,
                tradeDate = request.tradeDate,
                status = TrnStatus.SETTLED,
                comments = request.description ?: "Transfer to ${toAsset.code}"
            )

        val withdrawalTrns =
            trnService.save(
                fromPortfolio,
                TrnRequest(
                    fromPortfolio.id,
                    listOf(withdrawalInput)
                )
            )
        transactions.addAll(withdrawalTrns)

        // Create DEPOSIT transaction (amount received at destination)
        // Set cashAssetId = toAsset.id so the cash ladder can track this transaction
        val depositInput =
            TrnInput(
                callerRef = CallerRef(),
                assetId = toAsset.id,
                cashAssetId = toAsset.id,
                trnType = TrnType.DEPOSIT,
                tradeAmount = request.receivedAmount,
                tradeCurrency = toCurrency,
                cashCurrency = toCurrency,
                price = BigDecimal.ONE,
                tradeDate = request.tradeDate,
                status = TrnStatus.SETTLED,
                comments = request.description ?: "Transfer from ${fromAsset.code}"
            )

        val depositTrns =
            trnService.save(
                toPortfolio,
                TrnRequest(
                    toPortfolio.id,
                    listOf(depositInput)
                )
            )
        transactions.addAll(depositTrns)

        val fee = request.sentAmount - request.receivedAmount
        log.info(
            "Cash transfer completed: sent {} {}, received {} {} (fee: {}), from {} ({}) to {} ({})",
            request.sentAmount,
            fromCurrency,
            request.receivedAmount,
            toCurrency,
            fee,
            fromAsset.code,
            fromPortfolio.code,
            toAsset.code,
            toPortfolio.code
        )

        return CashTransferResponse(transactions)
    }

    /**
     * Get the currency code for a cash asset.
     * For CASH/PRIVATE markets, use priceSymbol which stores the currency.
     * For other markets, use the market's currency.
     */
    private fun getCashAssetCurrency(asset: com.beancounter.common.model.Asset): String =
        when (asset.marketCode) {
            CASH, "PRIVATE" -> asset.priceSymbol ?: asset.market.currency.code
            else -> asset.market.currency.code
        }
}