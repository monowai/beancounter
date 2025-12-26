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

/**
 * Represents an item within a classification standard.
 * Examples: "Information Technology" sector, "Consumer Electronics" industry.
 *
 * Items are organized hierarchically:
 * - SECTOR has no parent
 * - INDUSTRY has SECTOR as parent
 */
@Entity
@Table(
    name = "classification_item",
    uniqueConstraints = [UniqueConstraint(columnNames = ["standard_id", "level", "code"])]
)
data class ClassificationItem(
    @Id
    val id: String,
    @ManyToOne
    @JoinColumn(name = "standard_id")
    val standard: ClassificationStandard,
    @JsonIgnore
    @Column(name = "standard_id", insertable = false, updatable = false)
    val standardId: String = standard.id,
    @Enumerated(EnumType.STRING)
    val level: ClassificationLevel,
    val code: String,
    var name: String,
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "parent_id")
    val parent: ClassificationItem? = null,
    @JsonIgnore
    @Column(name = "parent_id", insertable = false, updatable = false)
    val parentId: String? = parent?.id
) {
    companion object {
        const val UNCLASSIFIED = "Unclassified"
    }
}