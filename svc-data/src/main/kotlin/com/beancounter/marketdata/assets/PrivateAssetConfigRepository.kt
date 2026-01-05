package com.beancounter.marketdata.assets

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

/**
 * Repository for PrivateAssetConfig entities.
 */
interface PrivateAssetConfigRepository : CrudRepository<PrivateAssetConfig, String> {
    /**
     * Find all configs for assets owned by a specific user.
     * Joins through the Asset table to filter by systemUser.
     */
    @Query(
        "SELECT c FROM PrivateAssetConfig c " +
            "JOIN Asset a ON c.assetId = a.id " +
            "WHERE a.systemUser.id = :userId " +
            "ORDER BY c.liquidationPriority"
    )
    fun findByUserId(
        @Param("userId") userId: String
    ): List<PrivateAssetConfig>

    /**
     * Find configs for multiple assets.
     */
    fun findByAssetIdIn(assetIds: Collection<String>): List<PrivateAssetConfig>
}