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

    /**
     * SAFEGUARD: Count market data records for an asset on a specific date
     */
    fun countByAssetIdAndPriceDate(
        assetId: String,
        priceDate: LocalDate
    ): Long

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
}