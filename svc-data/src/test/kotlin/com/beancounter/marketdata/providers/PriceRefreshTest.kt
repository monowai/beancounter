package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.key.KeyGenUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetHydrationService
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Tag("slow")
@EntityScan("com.beancounter.common.model")
internal class PriceRefreshTest {

    @Autowired
    private lateinit var priceRefresh: PriceRefresh

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var assetHydrationService: AssetHydrationService

    @Autowired
    private lateinit var marketService: MarketService

    @Test
    fun updatePrices() {
        val keyGenUtils = KeyGenUtils()
        val asset = assetRepository.save(
            Asset(
                code = keyGenUtils.id,
                market = NASDAQ
            )
        )
        val hydratedAsset = assetHydrationService.hydrateAsset(asset)
        assertThat(hydratedAsset).hasFieldOrProperty("market")
        val completable = priceRefresh.updatePrices()
        assertThat(completable.get()).isGreaterThanOrEqualTo(1)
    }
}
