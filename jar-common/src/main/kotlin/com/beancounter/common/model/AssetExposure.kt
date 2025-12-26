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
 * Represents weighted exposure of an Asset to a ClassificationItem.
 * Used primarily for ETFs where exposure is distributed across sectors.
 *
 * Example: VOO has 34.3% exposure to Information Technology sector.
 */
@Entity
@Table(
    name = "asset_exposure",
    uniqueConstraints = [UniqueConstraint(columnNames = ["asset_id", "standard_id", "item_id"])]
)
data class AssetExposure(
    @Id
    val id: String,
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "asset_id")
    val asset: Asset,
    @Column(name = "asset_id", insertable = false, updatable = false)
    val assetId: String = asset.id,
    @ManyToOne
    @JoinColumn(name = "standard_id")
    val standard: ClassificationStandard,
    @JsonIgnore
    @Column(name = "standard_id", insertable = false, updatable = false)
    val standardId: String = standard.id,
    @ManyToOne
    @JoinColumn(name = "item_id")
    val item: ClassificationItem,
    @JsonIgnore
    @Column(name = "item_id", insertable = false, updatable = false)
    val itemId: String = item.id,
    @Column(precision = 8, scale = 4)
    val weight: BigDecimal,
    val asOf: LocalDate = LocalDate.now()
)