package com.beancounter.marketdata.assets

import com.beancounter.common.model.AssetCategory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Initialise asset categories from config.
 */
@ConfigurationProperties(prefix = "beancounter.asset.categories")
@Component
class AssetCategoryConfig {
    var values: Collection<AssetCategory> = emptyList()
    var default: String = "EQUITY"
    private var categories = mutableMapOf<String, AssetCategory>()

    fun getCategories(): MutableMap<String, AssetCategory> {
        if (categories.isEmpty()) {
            for (assetCategory in values) {
                categories[assetCategory.id.uppercase()] = assetCategory
            }
        }
        return categories
    }

    /**
     * The category for [id], else the configured default. A default missing from
     * the configured categories is a configuration error.
     */
    fun resolve(id: String = default): AssetCategory =
        checkNotNull(get(id)) { "Default asset category '$default' is not configured" }

    fun get(id: String = default): AssetCategory? {
        if (id == "Common Stock") {
            return getCategories()["EQUITY"]
        }
        return getCategories()[id.uppercase()] ?: getCategories()[default.uppercase()]
    }
}