package com.beancounter.marketdata.assets

import com.beancounter.common.model.AssetCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.asset.categories")
@Component
data class AssetCategoryConfig @Autowired constructor(
    val values: Collection<AssetCategory>
) {
    private var categories = mutableMapOf<String, AssetCategory>()
    private val default: String = "Equity"

    @PostConstruct
    fun mapConfigs(): MutableMap<String, AssetCategory> {
        val results = mutableMapOf<String, AssetCategory>()
        for (assetCategory in values) {
            categories[assetCategory.id.uppercase()] = assetCategory
        }
        return results
    }

    fun get(id: String = default): AssetCategory? {
        if (id == "Common Stock") {
            return categories["EQUITY"]
        }
        return categories[id.uppercase()]
    }
}
