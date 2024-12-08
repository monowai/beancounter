package com.beancounter.marketdata.assets

import com.beancounter.common.model.AssetCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Initialise asset categories from config.
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.asset.categories")
@Component
data class AssetCategoryConfig
@Autowired
constructor(
    val values: Collection<AssetCategory>,
) {
    private var categories = mutableMapOf<String, AssetCategory>()
    private val default: String = "Equity"

    fun getCategories(): MutableMap<String, AssetCategory> {
        if (categories.isEmpty()) {
            for (assetCategory in values) {
                categories[assetCategory.id.uppercase()] = assetCategory
            }
        }
        return categories
    }

    fun get(id: String = default): AssetCategory? {
        if (id == "Common Stock") {
            return getCategories()["EQUITY"]
        }
        return getCategories()[id.uppercase()]
    }
}
