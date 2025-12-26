package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * Links an Asset to a ClassificationItem.
 * Used for direct classifications (e.g., Equity → Sector/Industry).
 */
@Entity
@Table(
    name = "asset_classification",
    uniqueConstraints = [UniqueConstraint(columnNames = ["asset_id", "standard_id", "level"])]
)
data class AssetClassification(
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
    @Enumerated(EnumType.STRING)
    val level: ClassificationLevel,
    val source: String,
    val asOf: LocalDate = LocalDate.now()
) {
    companion object {
        const val SOURCE_ALPHA_OVERVIEW = "ALPHA_OVERVIEW"
    }
}