package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Represents a holding within an ETF or fund.
 * Stores the top holdings with their weights for display purposes.
 *
 * Example: VOO holds AAPL at 7.2% weight.
 */
@Entity
@Table(
    name = "asset_holding",
    uniqueConstraints = [UniqueConstraint(columnNames = ["parent_asset_id", "symbol"])]
)
data class AssetHolding(
    @Id
    val id: String,
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "parent_asset_id")
    val parentAsset: Asset,
    @Column(name = "parent_asset_id", insertable = false, updatable = false)
    val parentAssetId: String = parentAsset.id,
    val symbol: String,
    val name: String? = null,
    @Column(precision = 8, scale = 4)
    val weight: BigDecimal,
    val asOf: LocalDate = LocalDate.now()
)