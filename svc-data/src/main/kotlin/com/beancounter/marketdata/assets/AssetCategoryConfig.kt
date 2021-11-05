package com.beancounter.marketdata.assets

import com.beancounter.common.model.AssetCategory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.asset.categories")
@Component
class AssetCategoryConfig {
    var default: String = "Equity"
    var values: Collection<AssetCategory>? = null
    private var categories = mutableMapOf<String, AssetCategory>()

    @PostConstruct
    fun mapConfigs() {
        for (assetCategory in values!!) {
            categories[assetCategory.id.uppercase()] = assetCategory
        }
    }

    fun get(id: String = default): AssetCategory? {
        if (id == "Common Stock") {
            return categories["EQUITY"]
        }
        return categories[id.uppercase()]
    }
}
