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
    val categoryBreakdown: Map<String, CategoryAllocation> = emptyMap()
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