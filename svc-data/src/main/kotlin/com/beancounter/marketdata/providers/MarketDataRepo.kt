package com.beancounter.marketdata.providers

import com.beancounter.common.model.MarketData
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.util.Optional

/**
 * MarketData CRUD repo.
 */
interface MarketDataRepo : CrudRepository<MarketData, String> {
    fun findByAssetIdAndPriceDate(assetId: String, date: LocalDate?): Optional<MarketData>
}
