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