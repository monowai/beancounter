package com.beancounter.marketdata.classification

import com.beancounter.common.model.AssetHolding
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

/**
 * Repository for AssetHolding entities.
 * Stores top holdings for ETFs and funds.
 */
interface AssetHoldingRepository : CrudRepository<AssetHolding, String> {
    /**
     * Find all holdings for a parent asset, ordered by weight descending.
     */
    @Query("SELECT h FROM AssetHolding h WHERE h.parentAsset.id = :assetId ORDER BY h.weight DESC")
    fun findByAssetId(
        @Param("assetId") assetId: String
    ): List<AssetHolding>

    /**
     * Delete all holdings for a parent asset.
     */
    @Modifying
    @Query("DELETE FROM AssetHolding h WHERE h.parentAsset.id = :assetId")
    fun deleteByAssetId(
        @Param("assetId") assetId: String
    )

    /**
     * Count holdings for an asset.
     */
    @Query("SELECT COUNT(h) FROM AssetHolding h WHERE h.parentAsset.id = :assetId")
    fun countByAssetId(
        @Param("assetId") assetId: String
    ): Long
}