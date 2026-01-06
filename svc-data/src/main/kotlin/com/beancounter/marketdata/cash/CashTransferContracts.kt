package com.beancounter.marketdata.cash

import com.beancounter.common.model.Trn
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Request to transfer cash between cash assets.
 * Creates a WITHDRAWAL from source and DEPOSIT to target.
 *
 * Both assets must be in the same currency.
 * Can transfer between portfolios.
 *
 * Supports transfer fees:
 * - sentAmount: Amount withdrawn from source (includes any sending fees)
 * - receivedAmount: Amount deposited to target (after any receiving fees)
 * - The difference (sentAmount - receivedAmount) represents total fees
 *
 * For backwards compatibility, if only sentAmount is provided,
 * receivedAmount defaults to the same value (no fees).
 */
data class CashTransferRequest(
    val fromPortfolioId: String,
    val fromAssetId: String,
    val toPortfolioId: String,
    val toAssetId: String,
    val sentAmount: BigDecimal,
    val receivedAmount: BigDecimal = sentAmount,
    val tradeDate: LocalDate = LocalDate.now(),
    val description: String? = null
)

/**
 * Response containing the created transactions for a cash transfer.
 */
data class CashTransferResponse(
    val transactions: List<Trn>
)