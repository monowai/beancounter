package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.markets.MarketService
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.PostUpdate
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * Populates the `@Transient` fields on [Asset] every time Hibernate hands one
 * back to the application. Covers all three lifecycle hooks:
 *
 *  - `@PostLoad`    fires after a fresh load from the DB (find / query).
 *  - `@PostPersist` fires after `em.persist()` — initial insert path.
 *  - `@PostUpdate`  fires after a managed entity flushes a change — covers
 *                   the `em.merge()` route Spring Data uses for `save()` on
 *                   application-assigned ids (Asset.id is set at creation).
 *
 * Without `@PostPersist` / `@PostUpdate`, the entity returned by `save()`
 * would carry null `market` / `assetCategory`, and the controller layer
 * would serialise it straight to JSON — the EVENT-1F class of bug. With all
 * three hooks the manual `AssetFinder.hydrateAsset` calls on save paths
 * become unnecessary; the method survives only for in-process Asset
 * construction (e.g. enricher output passed into save).
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
    @PostPersist
    @PostUpdate
    fun hydrate(asset: Asset) {
        if (asset.marketCode.isNotBlank()) {
            asset.market = marketServiceProvider.getObject().getMarket(asset.marketCode)
        }
        val categoryConfig = assetCategoryConfigProvider.getObject()
        asset.assetCategory = categoryConfig.get(asset.category) ?: categoryConfig.get()!!
    }
}