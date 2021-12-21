package com.beancounter.marketdata.assets

import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketService
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Necessary dependencies to get assets deployed.
 */
@Configuration
@Import(AssetService::class,
    // AssetRepository::class,
    EnrichmentFactory::class,
    EchoEnricher::class,
    MarketService::class,
    CurrencyService::class,
    AssetHydrationService::class,
    AssetCategoryConfig::class)
class AssetConfig