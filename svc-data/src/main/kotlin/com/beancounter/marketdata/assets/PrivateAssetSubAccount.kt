package com.beancounter.marketdata.assets

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * Sub-account within a composite policy asset (e.g. CPF OA/SA/MA/RA, ILP funds).
 *
 * Each sub-account has its own balance, return assumption, and liquidity flag.
 * Non-liquid sub-accounts (e.g. CPF Medisave) contribute to net worth but are
 * excluded from liquidation and payout drawdown.
 */
@Entity
@Table(name = "private_asset_sub_account")
data class PrivateAssetSubAccount(
    @Id
    val id: String = UUID.randomUUID().toString(),
    @Column(name = "asset_id", nullable = false)
    val assetId: String,
    @Column(nullable = false, length = 20)
    val code: String,
    @Column(name = "display_name", length = 100)
    val displayName: String? = null,
    @Column(precision = 19, scale = 4, nullable = false)
    val balance: BigDecimal = BigDecimal.ZERO,
    @Column(name = "expected_return_rate", precision = 5, scale = 4)
    val expectedReturnRate: BigDecimal? = null,
    @Column(name = "fee_rate", precision = 5, scale = 4)
    val feeRate: BigDecimal? = null,
    @Column(nullable = false)
    val liquid: Boolean = true
)