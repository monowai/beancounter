package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
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
}