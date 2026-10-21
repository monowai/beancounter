package com.beancounter.marketdata.watchlist

import com.beancounter.common.contracts.Payload
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.TargetDirection
import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Add an asset. When [addedPrice] is absent the current close is stamped.
 */
data class AddWatchlistItemRequest(
    val assetId: String,
    val addedPrice: BigDecimal? = null,
    val targetPrice: BigDecimal? = null
)

/**
 * Edit an item. Absent fields are left unchanged; [clearTarget] = true removes the target
 * and cannot be combined with [targetPrice].
 */
data class UpdateWatchlistItemRequest(
    val addedPrice: BigDecimal? = null,
    val targetPrice: BigDecimal? = null,
    val clearTarget: Boolean = false
)

/**
 * The caller's watchlist: their items, each with the latest price and server-computed changes.
 */
data class WatchlistView(
    val items: List<WatchlistItemView>
)

/**
 * An item with its latest price. Amounts are in [currency] (the asset's market currency) and on
 * today's share basis: [addedPrice] and [targetPrice] are rebased across splits since they were
 * entered. Percentages are fractions (0.25 = 25%), matching MarketData.changePercent.
 * Price-derived fields are null when the asset has no price; [toTarget] is also null without a
 * target. [targetHitOn] is the close date the target was reached on; null while it is armed or
 * without a target.
 */
data class WatchlistItemView(
    val id: String,
    val asset: Asset,
    val addedPrice: BigDecimal,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val addedOn: LocalDate,
    val targetPrice: BigDecimal?,
    val currency: Currency,
    val price: BigDecimal?,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val priceDate: LocalDate?,
    val changeOnDay: BigDecimal?,
    val changeOnDayPercent: BigDecimal?,
    val changeSinceAdded: BigDecimal?,
    val changeSinceAddedPercent: BigDecimal?,
    val toTarget: BigDecimal?,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val targetHitOn: LocalDate? = null
)

/**
 * Response wrapper for the caller's watchlist.
 */
data class WatchlistResponse(
    override val data: WatchlistView
) : Payload<WatchlistView>

/**
 * Response wrapper for one watchlist item.
 */
data class WatchlistItemResponse(
    override val data: WatchlistItemView
) : Payload<WatchlistItemView>

/**
 * A reached watchlist target. [targetPrice] and [close] are in [currency] and on today's share
 * basis (rebased across splits since [priceDate]). [direction] is the way the target was set to
 * be reached; [priceDate] is the date of the close that reached it.
 */
data class WatchlistAlertView(
    val id: String,
    val itemId: String,
    val asset: Asset,
    val currency: Currency,
    val targetPrice: BigDecimal,
    val direction: TargetDirection,
    val close: BigDecimal,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val priceDate: LocalDate,
    val createdAt: Instant
)

/**
 * Response wrapper for the caller's watchlist alerts, newest first.
 */
data class WatchlistAlertsResponse(
    override val data: List<WatchlistAlertView>
) : Payload<List<WatchlistAlertView>>