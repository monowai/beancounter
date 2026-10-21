package com.beancounter.marketdata.watchlist

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.util.Optional

/**
 * CRUD repo for [WatchlistAlert]. Every read is owner-scoped.
 */
interface WatchlistAlertRepository : CrudRepository<WatchlistAlert, String> {
    fun findByOwnerIdOrderByCreatedAtDesc(ownerId: String): List<WatchlistAlert>

    fun findByIdAndOwnerId(
        id: String,
        ownerId: String
    ): Optional<WatchlistAlert>

    /** Removing an item: its alerts go first. */
    @Modifying
    @Query("delete from WatchlistAlert a where a.item.id = :itemId")
    fun deleteByItemId(
        @Param("itemId") itemId: String
    ): Int

    /** Offboarding: bulk, idempotent delete of an owner's alerts. */
    @Modifying
    @Query("delete from WatchlistAlert a where a.owner.id = :ownerId")
    fun deleteByOwnerId(
        @Param("ownerId") ownerId: String
    ): Int

    /** Deleting an asset: the watchlist_alert -> asset FK is NO ACTION. */
    @Modifying
    @Query("delete from WatchlistAlert a where a.asset.id = :assetId")
    fun deleteByAssetId(
        @Param("assetId") assetId: String
    ): Int
}