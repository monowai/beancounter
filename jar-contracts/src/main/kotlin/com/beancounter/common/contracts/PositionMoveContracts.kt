package com.beancounter.common.contracts

data class PositionMoveRequest(
    val sourcePortfolioId: String,
    val targetPortfolioId: String,
    val assetId: String,
    val maintainCashBalances: Boolean = false
)

data class PositionMoveResponse(
    val movedCount: Int,
    val compensatingTransactions: Int = 0
)

/**
 * Outcome of consolidating one portfolio into another: every transaction in the
 * source is reassigned to the target, then the emptied source is deleted. Used
 * by the "downgrade to a single portfolio" (zen) wizard.
 */
data class PortfolioMergeResponse(
    val sourcePortfolioId: String,
    val targetPortfolioId: String,
    val assetsMoved: Int,
    val transactionsMoved: Int,
    val sourceDeleted: Boolean
)