package com.beancounter.marketdata.watchlist

import com.beancounter.common.model.WatchlistItem
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.util.Optional

/**
 * CRUD repo for [WatchlistItem].
 */
interface WatchlistItemRepository : CrudRepository<WatchlistItem, String> {
    fun findByWatchlistIdOrderByAssetCode(watchlistId: String): List<WatchlistItem>

    fun findByIdAndWatchlistId(
        id: String,
        watchlistId: String
    ): Optional<WatchlistItem>

    fun existsByWatchlistIdAndAssetId(
        watchlistId: String,
        assetId: String
    ): Boolean

    @Query(
        "select i.watchlist.id as watchlistId, count(i) as itemCount from WatchlistItem i where i.watchlist.owner.id = :ownerId group by i.watchlist.id"
    )
    fun countByOwnerId(
        @Param("ownerId") ownerId: String
    ): List<WatchlistItemCount>

    @Modifying
    @Query("delete from WatchlistItem i where i.watchlist.id = :watchlistId")
    fun deleteByWatchlistId(
        @Param("watchlistId") watchlistId: String
    ): Int

    @Modifying
    @Query(
        "delete from WatchlistItem i where i.watchlist.id in (select w.id from Watchlist w where w.owner.id = :ownerId)"
    )
    fun deleteByOwnerId(
        @Param("ownerId") ownerId: String
    ): Int

    /**
     * Drop the asset from every watchlist. Required before deleting an asset: the
     * watchlist_item -> asset FK is NO ACTION.
     */
    @Modifying
    @Query("delete from WatchlistItem i where i.asset.id = :assetId")
    fun deleteByAssetId(
        @Param("assetId") assetId: String
    ): Int
}

/**
 * Projection: number of items on a watchlist.
 */
interface WatchlistItemCount {
    val watchlistId: String
    val itemCount: Long
}