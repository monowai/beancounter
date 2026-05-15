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
    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.marketCode = :marketCode"
    )
    fun findByMarketCode(
        @Param("marketCode") marketCode: String
    ): List<Asset>

    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.marketCode = :marketCode AND a.code = :code"
    )
    fun findByMarketCodeAndCode(
        @Param("marketCode") marketCode: String,
        @Param("code") code: String
    ): Optional<Asset>

    /**
     * Find all active assets with non-empty codes for price refresh.
     * Excludes:
     * - Inactive assets (delisted, etc.)
     * - Assets with empty codes (data quality issues)
     * - Private market assets (user-defined assets without external pricing)
     */
    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.status = com.beancounter.common.model.Status.Active " +
            "AND a.code IS NOT NULL AND a.code <> '' " +
            "AND a.marketCode <> 'PRIVATE'"
    )
    fun findActiveAssetsForPricing(): Stream<Asset>

    /**
     * Find active assets with at least one positive net holding across all
     * transactions. Used by the scheduled price refresh to skip orphans —
     * assets sold-out, never bought, or never traded. Refreshing those wastes
     * provider quota and floods Sentry with provider errors (DATA-2H, DATA-3A)
     * for tickers no user holds anymore.
     *
     * Net position = SUM(BUY/ADD) - SUM(SELL/REDUCE). SPLIT/DIVI/cash flows
     * are excluded — they don't change quantity in the same way.
     */
    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.status = com.beancounter.common.model.Status.Active " +
            "AND a.code IS NOT NULL AND a.code <> '' " +
            "AND a.marketCode <> 'PRIVATE' " +
            "AND EXISTS (" +
            "  SELECT 1 FROM Trn t WHERE t.asset = a " +
            "  GROUP BY t.asset " +
            "  HAVING SUM(" +
            "    CASE " +
            "      WHEN t.trnType IN (" +
            "        com.beancounter.common.model.TrnType.BUY, " +
            "        com.beancounter.common.model.TrnType.ADD" +
            "      ) THEN t.quantity " +
            "      WHEN t.trnType IN (" +
            "        com.beancounter.common.model.TrnType.SELL, " +
            "        com.beancounter.common.model.TrnType.REDUCE" +
            "      ) THEN -t.quantity " +
            "      ELSE 0 " +
            "    END" +
            "  ) > 0" +
            ")"
    )
    fun findHeldAssetsForPricing(): Stream<Asset>

    /**
     * Find active assets on the synthetic INDEX market. These are reference
     * benchmarks (no holdings) that the scheduled refresh fetches alongside
     * held assets so dashboards always show fresh index quotes.
     */
    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.status = com.beancounter.common.model.Status.Active " +
            "AND a.code IS NOT NULL AND a.code <> '' " +
            "AND a.marketCode = 'INDEX'"
    )
    fun findActiveIndexAssets(): List<Asset>

    /**
     * Find all assets owned by a specific user with a specific category.
     */
    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.systemUser.id = :systemUserId AND a.category = :category"
    )
    fun findBySystemUserIdAndCategory(
        @Param("systemUserId") systemUserId: String,
        @Param("category") category: String
    ): List<Asset>

    /**
     * Find all assets owned by a specific user.
     */
    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.systemUser.id = :systemUserId"
    )
    fun findBySystemUserId(
        @Param("systemUserId") systemUserId: String
    ): List<Asset>

    /**
     * Search user's assets by code or name (case-insensitive partial match).
     */
    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.systemUser.id = :userId " +
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
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.status = com.beancounter.common.model.Status.Active " +
            "AND UPPER(a.category) IN ('ETF', 'EXCHANGE TRADED FUND') " +
            "AND a.code IS NOT NULL AND a.code <> ''"
    )
    fun findActiveEtfs(): List<Asset>

    /**
     * Find all active Equity assets for classification refresh.
     */
    @Query(
        "SELECT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE a.status = com.beancounter.common.model.Status.Active " +
            "AND UPPER(a.category) IN ('EQUITY', 'COMMON STOCK') " +
            "AND a.code IS NOT NULL AND a.code <> ''"
    )
    fun findActiveEquities(): List<Asset>

    fun countByAccountingTypeId(id: String): Long

    /**
     * Group asset count by [Asset.marketCode]. Surfaced as the
     * `beancounter.asset.count.by_market` MultiGauge — see
     * [com.beancounter.marketdata.metrics.EntityCountMetrics].
     */
    @Query(
        "SELECT new com.beancounter.marketdata.metrics.MarketCount(a.marketCode, COUNT(a)) " +
            "FROM Asset a GROUP BY a.marketCode ORDER BY COUNT(a) DESC"
    )
    fun countByMarketCode(): List<com.beancounter.marketdata.metrics.MarketCount>

    /**
     * Search all assets in the database by code or name (case-insensitive partial match).
     * Used for local-only asset lookup to find assets without calling external providers.
     * Returns distinct assets to avoid duplicates when same asset is held in multiple portfolios.
     */
    @Query(
        "SELECT DISTINCT a FROM Asset a LEFT JOIN FETCH a.systemUser LEFT JOIN FETCH a.accountingType " +
            "WHERE (LOWER(a.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY a.code"
    )
    fun searchByCodeOrName(
        @Param("keyword") keyword: String
    ): List<Asset>
}