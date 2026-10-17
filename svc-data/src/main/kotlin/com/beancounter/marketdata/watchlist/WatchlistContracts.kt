package com.beancounter.marketdata.watchlist

import com.beancounter.common.contracts.Payload
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Create a watchlist. Code is upper-cased and must be unique for the owner.
 */
data class CreateWatchlistRequest(
    val code: String,
    val name: String
)

/**
 * Rename a watchlist. Absent fields are left unchanged.
 */
data class UpdateWatchlistRequest(
    val code: String? = null,
    val name: String? = null
)

/**
 * Add an asset. When [addedPrice] is absent the latest known close is stamped.
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
 * One row of the caller's watchlists.
 */
data class WatchlistSummary(
    val id: String,
    val code: String,
    val name: String,
    val itemCount: Int
)

/**
 * A watchlist with its items, each carrying the latest price and server-computed changes.
 */
data class WatchlistView(
    val id: String,
    val code: String,
    val name: String,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val createdAt: LocalDate,
    val items: List<WatchlistItemView>
)

/**
 * An item with its latest price. Amounts are in [currency] (the asset's market currency).
 * Percentages are fractions (0.25 = 25%), matching MarketData.changePercent. Price-derived
 * fields are null when the asset has no price; [toTarget] is also null without a target.
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
    val toTarget: BigDecimal?
)

/**
 * Response wrapper for the caller's watchlists.
 */
data class WatchlistsResponse(
    override val data: List<WatchlistSummary>
) : Payload<List<WatchlistSummary>>

/**
 * Response wrapper for one watchlist.
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