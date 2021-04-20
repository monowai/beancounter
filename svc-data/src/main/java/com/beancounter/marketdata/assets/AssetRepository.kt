package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.stream.Stream

/**
 * CRUD interface for Asset details.
 */
interface AssetRepository : CrudRepository<Asset, String> {
    fun findByMarketCodeAndCode(marketCode: String, code: String): Optional<Asset>

    @Query("select a from Asset a")
    @Transactional(readOnly = true)
    fun findAllAssets(): Stream<Asset>?
}
