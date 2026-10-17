package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CheckConstraint
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
 * An asset on a [Watchlist], with the price it was added at (in the asset's market currency)
 * and an optional target price. Both are stored on the share basis of the day they were
 * entered ([addedOn], [targetOn]); readers rebase them across later splits.
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_watchlist_item_asset",
            columnNames = ["watchlist_id", "asset_id"]
        )
    ],
    // A target always carries the date it was entered (its share basis). Mirrors V43.
    check = [
        CheckConstraint(
            name = "ck_watchlist_item_target_on",
            constraint = "\"target_price\" IS NULL OR \"target_on\" IS NOT NULL"
        )
    ]
)
data class WatchlistItem(
    @Id
    val id: String = KeyGenUtils().id,
    @ManyToOne
    @JoinColumn(name = "watchlist_id", nullable = false)
    @JsonIgnore
    val watchlist: Watchlist,
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
    var targetOn: LocalDate? = null
)