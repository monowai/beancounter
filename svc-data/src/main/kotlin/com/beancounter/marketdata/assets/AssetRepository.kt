package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.stream.Stream

/**
 * CRUD interface for Asset details.
 */
interface AssetRepository : CrudRepository<Asset, String> {
    fun findByMarketCode(marketCode: String): List<Asset>

    fun findByMarketCodeAndCode(
        marketCode: String,
        code: String
    ): Optional<Asset>

    @Query("select a from Asset a")
    fun findAllAssets(): Stream<Asset>

    /**
     * Find all assets owned by a specific user with a specific category.
     */
    fun findBySystemUserIdAndCategory(
        systemUserId: String,
        category: String
    ): List<Asset>

    /**
     * Find all assets owned by a specific user.
     */
    fun findBySystemUserId(systemUserId: String): List<Asset>

    /**
     * Search user's assets by code (case-insensitive partial match).
     */
    @Query(
        "SELECT a FROM Asset a WHERE a.systemUser.id = :userId " +
            "AND LOWER(a.code) LIKE LOWER(CONCAT('%', :keyword, '%'))"
    )
    fun searchByUserAndCode(
        @Param("userId") userId: String,
        @Param("keyword") keyword: String
    ): List<Asset>
}