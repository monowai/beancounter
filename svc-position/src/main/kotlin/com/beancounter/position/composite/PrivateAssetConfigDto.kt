package com.beancounter.position.composite

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Lean DTO mirroring svc-data's PrivateAssetConfig response for the fields
 * needed by composite valuation. Unknown fields are ignored so svc-data can
 * evolve its schema without breaking svc-position.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PrivateAssetConfigDto(
    val assetId: String,
    val policyType: String? = null,
    val currency: String? = null,
    val payoutAge: Int? = null,
    val monthlyPayoutAmount: BigDecimal? = null,
    val cpfLifePlan: String? = null,
    val cpfPayoutStartAge: Int? = null,
    val lockedUntilDate: LocalDate? = null,
    val subAccounts: List<SubAccountDto> = emptyList()
) {
    fun isComposite(): Boolean = subAccounts.isNotEmpty()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubAccountDto(
    val code: String,
    val displayName: String? = null,
    val balance: BigDecimal = BigDecimal.ZERO,
    val expectedReturnRate: BigDecimal? = null,
    val feeRate: BigDecimal? = null,
    val liquid: Boolean = true
)

/**
 * Wrapper for the GET /assets/config/{id} response shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PrivateAssetConfigResponseDto(
    val data: PrivateAssetConfigDto
)