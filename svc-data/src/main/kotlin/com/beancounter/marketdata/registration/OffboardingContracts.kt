package com.beancounter.marketdata.registration

/**
 * Summary of user's data that can be deleted during offboarding.
 */
data class OffboardingSummaryResponse(
    val portfolioCount: Int,
    val assetCount: Int,
    val taxRateCount: Int
)

/**
 * Result of a deletion operation during offboarding.
 */
data class OffboardingResult(
    val success: Boolean,
    val deletedCount: Int,
    val type: String,
    val message: String? = null
)