package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

/**
 * An asset on its owner's watchlist. Each [SystemUser] has one watchlist: the items they own.
 * Records the price it was added at (in the asset's market currency) and an optional target.
 * Both are stored on the share basis of the day they were entered ([addedOn], [targetOn]);
 * readers rebase them across later splits. A target also carries its [targetDirection], fixed
 * when it is set, and [targetHitOn], the close date it was reached (null while armed).
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_watchlist_item_owner_asset",
            columnNames = ["owner_id", "asset_id"]
        )
    ],
    // A target always carries the date it was entered (its share basis, V43) and the
    // direction it is reached in (V44).
    check = [
        CheckConstraint(
            name = "ck_watchlist_item_target_on",
            constraint = "\"target_price\" IS NULL OR \"target_on\" IS NOT NULL"
        ),
        CheckConstraint(
            name = "ck_watchlist_item_target_direction",
            constraint = "\"target_price\" IS NULL OR \"target_direction\" IS NOT NULL"
        )
    ]
)
data class WatchlistItem(
    @Id
    val id: String = KeyGenUtils().id,
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    val owner: SystemUser,
    @ManyToOne
    @JoinColumn(name = "asset_id", nullable = false)
    val asset: Asset,
    @Column(precision = 19, scale = 6, nullable = false)
    var addedPrice: BigDecimal,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val addedOn: LocalDate,
    @Column(precision = 19, scale = 6)
    var targetPrice: BigDecimal? = null,
    // Date the target was entered; set whenever targetPrice is (ck_watchlist_item_target_on).
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    var targetOn: LocalDate? = null,
    // Set whenever targetPrice is (ck_watchlist_item_target_direction).
    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    var targetDirection: TargetDirection? = null,
    // Close date the target was reached on; null while armed. Reset whenever the target changes.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    var targetHitOn: LocalDate? = null
)