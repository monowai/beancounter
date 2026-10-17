package com.beancounter.marketdata.watchlist

import com.beancounter.common.model.Watchlist
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.util.Optional

/**
 * CRUD repo for [Watchlist]. Every read is owner-scoped.
 */
interface WatchlistRepository : CrudRepository<Watchlist, String> {
    fun findByOwnerIdOrderByName(ownerId: String): List<Watchlist>

    fun findByIdAndOwnerId(
        id: String,
        ownerId: String
    ): Optional<Watchlist>

    fun existsByOwnerIdAndCode(
        ownerId: String,
        code: String
    ): Boolean

    /**
     * Bulk, idempotent delete of an owner's watchlists. Callers clear the items first
     * ([WatchlistItemRepository.deleteByOwnerId]).
     */
    @Modifying
    @Query("delete from Watchlist w where w.owner.id = :ownerId")
    fun deleteByOwnerId(
        @Param("ownerId") ownerId: String
    ): Int
}