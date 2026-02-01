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

    /**
     * Find all active assets with non-empty codes for price refresh.
     * Excludes:
     * - Inactive assets (delisted, etc.)
     * - Assets with empty codes (data quality issues)
     * - Private market assets (user-defined assets without external pricing)
     */
    @Query(
        "select a from Asset a where a.status = com.beancounter.common.model.Status.Active " +
            "and a.code is not null and a.code <> '' " +
            "and a.marketCode <> 'PRIVATE'"
    )
    fun findActiveAssetsForPricing(): Stream<Asset>

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
     * Search user's assets by code or name (case-insensitive partial match).
     */
    @Query(
        "SELECT a FROM Asset a WHERE a.systemUser.id = :userId " +
            "AND (LOWER(a.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')))"
    )
    fun searchByUserAndCodeOrName(
        @Param("userId") userId: String,
        @Param("keyword") keyword: String
    ): List<Asset>

    /**
     * Find all active ETF assets for classification refresh.
     */
    @Query(
        "SELECT a FROM Asset a WHERE a.status = com.beancounter.common.model.Status.Active " +
            "AND UPPER(a.category) IN ('ETF', 'EXCHANGE TRADED FUND') " +
            "AND a.code IS NOT NULL AND a.code <> ''"
    )
    fun findActiveEtfs(): List<Asset>

    /**
     * Find all active Equity assets for classification refresh.
     */
    @Query(
        "SELECT a FROM Asset a WHERE a.status = com.beancounter.common.model.Status.Active " +
            "AND UPPER(a.category) IN ('EQUITY', 'COMMON STOCK') " +
            "AND a.code IS NOT NULL AND a.code <> ''"
    )
    fun findActiveEquities(): List<Asset>

    /**
     * Search all assets in the database by code or name (case-insensitive partial match).
     * Used for local-only asset lookup to find assets without calling external providers.
     * Returns distinct assets to avoid duplicates when same asset is held in multiple portfolios.
     */
    @Query(
        "SELECT DISTINCT a FROM Asset a WHERE " +
            "(LOWER(a.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY a.code"
    )
    fun searchByCodeOrName(
        @Param("keyword") keyword: String
    ): List<Asset>
}