package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.Optional

/**
 * MarketData CRUD repo.
 */
interface MarketDataRepo : CrudRepository<MarketData, String> {
    fun findByAssetIdAndPriceDate(
        assetId: String,
        date: LocalDate?
    ): Optional<MarketData>

    /**
     * Every row for an asset on a given date, across all sources. The unique
     * key on `MarketData` is `(source, asset_id, price_date)`, so a single
     * (asset_id, price_date) tuple can have multiple rows when more than one
     * provider supplies the asset. Splits / dividends apply to the asset
     * regardless of provider, so the repair endpoint stamps every match.
     */
    fun findAllByAssetIdAndPriceDate(
        assetId: String,
        priceDate: LocalDate
    ): List<MarketData>

    fun findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc(
        asset: Asset,
        priceDate: LocalDate
    ): Optional<MarketData>

    /**
     * Find the most recent market data for an asset strictly before the given date.
     * Used to calculate previousClose when API doesn't provide it.
     */
    fun findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(
        asset: Asset,
        priceDate: LocalDate
    ): Optional<MarketData>

    @Query(
        "SELECT md FROM MarketData md JOIN FETCH md.asset WHERE md.asset IN :assets AND md.priceDate = :priceDate"
    )
    fun findByAssetInAndPriceDate(
        @Param("assets") assets: Collection<Asset>,
        @Param("priceDate") priceDate: LocalDate
    ): List<MarketData>

    @Query(
        "SELECT md FROM MarketData md JOIN FETCH md.asset WHERE md.asset IN :assets AND md.priceDate IN :dates"
    )
    fun findByAssetInAndPriceDateIn(
        @Param("assets") assets: Collection<Asset>,
        @Param("dates") dates: Collection<LocalDate>
    ): List<MarketData>

    @Query(
        "SELECT md FROM MarketData md JOIN FETCH md.asset a " +
            "WHERE a.id = :assetId AND md.priceDate BETWEEN :from AND :to " +
            "ORDER BY md.priceDate ASC"
    )
    fun findPriceHistory(
        @Param("assetId") assetId: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<MarketData>

    /**
     * SAFEGUARD: Count market data records for an asset on a specific date
     */
    fun countByAssetIdAndPriceDate(
        assetId: String,
        priceDate: LocalDate
    ): Long

    @Query("SELECT MIN(md.priceDate) FROM MarketData md WHERE md.asset.id = :assetId")
    fun findEarliestPriceDateByAssetId(
        @Param("assetId") assetId: String
    ): LocalDate?

    @Query("SELECT MAX(md.priceDate) FROM MarketData md WHERE md.asset.id = :assetId")
    fun findLatestPriceDateByAssetId(
        @Param("assetId") assetId: String
    ): LocalDate?

    /**
     * Bulk window load used by getBulkMarketData to eliminate N+1 nearest-prior fallback
     * queries. Returns every price row for the given assets between [from] and [to] inclusive
     * in a single query, ordered ascending. Caller resolves exact-date hits + nearest-prior
     * fallback (weekends/holidays) in memory.
     */
    @Query(
        "SELECT md FROM MarketData md JOIN FETCH md.asset a " +
            "WHERE a IN :assets AND md.priceDate BETWEEN :from AND :to " +
            "ORDER BY md.priceDate ASC"
    )
    fun findByAssetInAndPriceDateBetween(
        @Param("assets") assets: Collection<Asset>,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<MarketData>

    /**
     * Find stored prices that represent corporate events (dividend or split).
     */
    @Query(
        "SELECT md FROM MarketData md WHERE md.asset.id = :assetId AND (md.dividend > 0 OR md.split <> 1)"
    )
    fun findEventsByAssetId(
        @Param("assetId") assetId: String
    ): List<MarketData>

    /**
     * Delete all market data for a specific asset.
     * Used when cascading asset deletion.
     */
    fun deleteByAssetId(assetId: String)

    /**
     * Group MarketData row count by the owning asset's marketCode.
     * Surfaced as the `beancounter.market_data.count.by_market` MultiGauge.
     * Note: heavy query on large tables — refreshed via @Scheduled rather than
     * on every actuator scrape (see EntityCountMetrics).
     */
    @Query(
        "SELECT new com.beancounter.marketdata.metrics.MarketCount(md.asset.marketCode, COUNT(md)) " +
            "FROM MarketData md GROUP BY md.asset.marketCode ORDER BY COUNT(md) DESC"
    )
    fun countByMarketCode(): List<com.beancounter.marketdata.metrics.MarketCount>
}