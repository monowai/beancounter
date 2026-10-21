package com.beancounter.marketdata.watchlist

import com.beancounter.common.model.WatchlistItem
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * CRUD repo for [WatchlistItem]. Every read is owner-scoped.
 */
interface WatchlistItemRepository : CrudRepository<WatchlistItem, String> {
    fun findByOwnerIdOrderByAssetCode(ownerId: String): List<WatchlistItem>

    fun findByIdAndOwnerId(
        id: String,
        ownerId: String
    ): Optional<WatchlistItem>

    /** Items with a target that has not been reached yet. */
    fun findByTargetPriceIsNotNullAndTargetHitOnIsNull(): List<WatchlistItem>

    /**
     * Stamp an armed target as reached on [hitOn], only while it is still the target the caller
     * checked. Returns 0 when another check, or an edit, got there first: the alert is not raised.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        "update WatchlistItem i set i.targetHitOn = :hitOn where i.id = :id and i.targetHitOn is null " +
            "and i.targetOn = :targetOn and i.targetPrice = :targetPrice"
    )
    fun markHit(
        @Param("id") id: String,
        @Param("targetOn") targetOn: LocalDate,
        @Param("targetPrice") targetPrice: BigDecimal,
        @Param("hitOn") hitOn: LocalDate
    ): Int

    fun existsByOwnerIdAndAssetId(
        ownerId: String,
        assetId: String
    ): Boolean

    /**
     * Offboarding: bulk, idempotent delete of an owner's watchlist.
     */
    @Modifying
    @Query("delete from WatchlistItem i where i.owner.id = :ownerId")
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