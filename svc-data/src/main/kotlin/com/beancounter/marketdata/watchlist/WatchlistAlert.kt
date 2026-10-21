package com.beancounter.marketdata.watchlist

import com.beancounter.common.model.Asset
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TargetDirection
import com.beancounter.common.model.WatchlistItem
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * A watchlist target that was reached: one row each time an armed item's close crosses its
 * target. [targetPrice] and [close] are on the share basis of [priceDate]; readers rebase them
 * across later splits. Owned by the item's [SystemUser]. Mirrors V44.
 */
@Entity
@Table(name = "watchlist_alert")
data class WatchlistAlert(
    @Id
    val id: String = KeyGenUtils().id,
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: SystemUser,
    // Alerts go with their item (ON DELETE CASCADE in V44 and in ddl-auto schemas).
    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val item: WatchlistItem,
    @ManyToOne
    @JoinColumn(name = "asset_id", nullable = false)
    val asset: Asset,
    @Column(precision = 19, scale = 6, nullable = false)
    val targetPrice: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(length = 5, nullable = false)
    val direction: TargetDirection,
    @Column(precision = 19, scale = 6, nullable = false)
    val close: BigDecimal,
    @Column(nullable = false)
    val priceDate: LocalDate,
    @Column(nullable = false)
    val createdAt: Instant
)