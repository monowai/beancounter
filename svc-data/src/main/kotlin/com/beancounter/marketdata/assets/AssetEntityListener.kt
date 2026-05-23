package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.markets.MarketService
import jakarta.persistence.PostLoad
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * Populates the `@Transient` fields on [Asset] every time JPA loads one
 * (`@PostLoad`). Replaces the historical pattern of calling
 * `AssetFinder.hydrateAsset(...)` at every read site — Hibernate now wires
 * `market` and `assetCategory` for callers automatically.
 *
 * Registered via `META-INF/orm.xml` so jar-common keeps no svc-data dependency.
 * Spring's `SpringBeanContainer` (default in Spring Boot 3) supplies the
 * constructor dependencies via DI.
 *
 * Dependencies are wrapped in [ObjectProvider] to defer resolution past
 * EntityManagerFactory construction — without this, MarketService → MarketConfig
 * → CurrencyService → CurrencyRepository pulls EMF back into its own init cycle.
 */
@Component
class AssetEntityListener(
    private val marketServiceProvider: ObjectProvider<MarketService>,
    private val assetCategoryConfigProvider: ObjectProvider<AssetCategoryConfig>
) {
    @PostLoad
    fun hydrate(asset: Asset) {
        if (asset.marketCode.isNotBlank()) {
            asset.market = marketServiceProvider.getObject().getMarket(asset.marketCode)
        }
        val categoryConfig = assetCategoryConfigProvider.getObject()
        asset.assetCategory = categoryConfig.get(asset.category) ?: categoryConfig.get()!!
    }
}