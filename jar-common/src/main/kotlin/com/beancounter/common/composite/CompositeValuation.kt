package com.beancounter.common.composite

import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Single source of truth for composite-asset (CPF / ILP / generic pension)
 * balance roll-ups. Both `svc-position` (synthesising a `Position` for the
 * `/positions/composite/{assetId}` endpoint) and `svc-retire` (adjusting a
 * Plan's `totalAssets` / `liquidAssets` when projecting) delegate here so
 * the math lives in exactly one place.
 *
 * Inputs are deliberately primitive (`policyType` + sub-account list) so
 * the support class is decoupled from any service's DTO shape. Callers
 * adapt their own DTO (svc-position's `PrivateAssetConfigDto`, svc-retire's
 * `PrivateAssetConfigDto`, etc.) into the lean [SubAccountBalance] list.
 *
 * Returns null when the policy type isn't composite-aware so callers can
 * fall back to the default per-asset balance lookup without special-casing.
 */
@Component
class CompositeValuation {
    fun value(
        policyType: String?,
        subAccounts: List<SubAccountBalance>
    ): CompositeBalance? {
        if (policyType !in supportedPolicyTypes) return null

        var total = BigDecimal.ZERO
        var liquid = BigDecimal.ZERO
        var nonLiquid = BigDecimal.ZERO
        val byCode = linkedMapOf<String, BigDecimal>()

        for (sub in subAccounts) {
            total = total.add(sub.balance)
            if (sub.liquid) {
                liquid = liquid.add(sub.balance)
            } else {
                nonLiquid = nonLiquid.add(sub.balance)
            }
            byCode[sub.code] = sub.balance
        }

        return CompositeBalance(
            total = total,
            liquid = liquid,
            nonLiquid = nonLiquid,
            byCode = byCode
        )
    }

    companion object {
        /** Policy types this support class knows how to roll up. */
        val supportedPolicyTypes: Set<String> = setOf("CPF")
    }
}

/**
 * Lean per-sub-account input. Callers map their own DTO into this list.
 *
 * @property liquid `true` when the sub-account contributes to spendable wealth
 * today (CPF OA / SA / RA). `false` for locked buckets (CPF MA pre-55).
 */
data class SubAccountBalance(
    val code: String,
    val balance: BigDecimal,
    val liquid: Boolean = true
)

/**
 * Rolled-up balance for one composite asset.
 *
 * @property total sum of all sub-account balances
 * @property liquid sum of sub-account balances with `liquid = true`
 * @property nonLiquid sum of sub-account balances with `liquid = false`
 * @property byCode per-sub-account balance map (preserves caller's order)
 */
data class CompositeBalance(
    val total: BigDecimal,
    val liquid: BigDecimal,
    val nonLiquid: BigDecimal,
    val byCode: Map<String, BigDecimal>
)