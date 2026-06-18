package com.beancounter.common.contracts

import java.math.BigDecimal

/**
 * Asset allocation breakdown by category.
 * Used for retirement planning to determine actual portfolio allocation.
 */
data class AllocationData(
    val cashAllocation: BigDecimal = BigDecimal.ZERO,
    val equityAllocation: BigDecimal = BigDecimal.ZERO,
    val housingAllocation: BigDecimal = BigDecimal.ZERO,
    val totalValue: BigDecimal = BigDecimal.ZERO,
    val currency: String = "USD",
    val categoryBreakdown: Map<String, CategoryAllocation> = emptyMap(),
    /**
     * Asset ids represented in the underlying portfolio positions. Lets
     * callers deduplicate against config-driven rollups (e.g. composite
     * pensions linked into a portfolio via a BALANCE trn) so a single
     * holding doesn't get counted twice in plan totals.
     */
    val heldAssetIds: Set<String> = emptySet(),
    /**
     * Sum of composite-policy sub-account balances flagged liquid (CPF
     * OA / SA / RA pre-55), in [currency]. Allows the projection engine
     * to keep these balances in the spendable bucket while treating the
     * locked portion as non-liquid — without re-running the per-asset
     * sub-account rollup itself.
     */
    val compositeLiquid: BigDecimal = BigDecimal.ZERO,
    /**
     * Sum of composite-policy sub-account balances flagged non-liquid
     * (CPF MA, future RA-post-55), in [currency]. The projection moves
     * this amount from the spendable pot to the non-spendable bucket so
     * the locked CPF balances are not drawn down alongside cash + ETFs.
     */
    val compositeNonLiquid: BigDecimal = BigDecimal.ZERO
)

/**
 * Detailed allocation for a single category.
 */
data class CategoryAllocation(
    val category: String,
    val marketValue: BigDecimal,
    val percentage: BigDecimal
)

/**
 * Response wrapper for allocation data.
 */
data class AllocationResponse(
    override val data: AllocationData = AllocationData()
) : Payload<AllocationData>