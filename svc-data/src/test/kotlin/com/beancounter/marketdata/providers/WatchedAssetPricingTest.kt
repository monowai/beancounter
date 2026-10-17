package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Watchlist
import com.beancounter.common.model.WatchlistItem
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.PRIVATE
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.watchlist.WatchlistItemRepository
import com.beancounter.marketdata.watchlist.WatchlistRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * Watched-only assets have no holdings, so the scheduled refresh must price them alongside
 * held assets, with the same exclusions (inactive, blank code, PRIVATE market).
 */
@SpringMvcDbTest
class WatchedAssetPricingTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var alphaPriceService: AlphaPriceService

    @Autowired
    private lateinit var priceRefresh: PriceRefresh

    @Autowired
    private lateinit var assetFinder: AssetFinder

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var systemUserRepository: SystemUserRepository

    @Autowired
    private lateinit var watchlistRepository: WatchlistRepository

    @Autowired
    private lateinit var watchlistItemRepository: WatchlistItemRepository

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var cacheManager: CacheManager

    @BeforeEach
    fun stubProvider() {
        cacheManager.getCache("provider")?.clear()
        whenever(alphaPriceService.getId()).thenReturn(AlphaPriceService.ID)
        whenever(alphaPriceService.isMarketSupported(any())).thenReturn(true)
        whenever(alphaPriceService.isApiSupported()).thenReturn(true)
        whenever(alphaPriceService.getDate(any(), any())).thenReturn(dateUtils.date)
    }

    private fun watch(
        code: String,
        market: Market = NASDAQ,
        status: Status = Status.Active
    ): Asset {
        val owner =
            systemUserRepository.save(
                SystemUser(id = "watched-$code", email = "watched-$code@test.com")
            )
        val asset =
            assetRepository.save(
                Asset(
                    code = code,
                    id = KeyGenUtils().id,
                    market = market,
                    marketCode = market.code,
                    status = status
                )
            )
        val watchlist =
            watchlistRepository.save(
                Watchlist(owner = owner, code = code, name = code, createdAt = dateUtils.date)
            )
        watchlistItemRepository.save(
            WatchlistItem(
                watchlist = watchlist,
                asset = asset,
                addedPrice = BigDecimal.TEN,
                addedOn = dateUtils.date
            )
        )
        return asset
    }

    @Test
    fun `should include an asset that is only on a watchlist in the refresh set`() {
        val watched = watch("WATCHONLY")

        assertThat(assetFinder.findHeldAssetsForPricing().map { it.id }).doesNotContain(watched.id)
        assertThat(assetFinder.findWatchedAssetsForPricing().map { it.id }).contains(watched.id)

        priceRefresh.updatePrices()

        // Prices persist asynchronously, so the provider request is the observable signal
        // that the refresh enrolled the asset.
        val requests = argumentCaptor<PriceRequest>()
        verify(alphaPriceService, atLeastOnce()).getMarketData(requests.capture())
        assertThat(requests.allValues.flatMap { request -> request.assets.map { it.code } })
            .contains(watched.code)
    }

    @Test
    fun `should exclude private and inactive watched assets like held ones`() {
        val privateAsset = watch("WATCHPRIVATE", market = Market(PRIVATE))
        val inactive = watch("WATCHINACTIVE", status = Status.Inactive)
        val active = watch("WATCHACTIVE")

        val refreshSet = assetFinder.findWatchedAssetsForPricing().map { it.id }

        assertThat(refreshSet).contains(active.id)
        assertThat(refreshSet).doesNotContain(privateAsset.id, inactive.id)
    }
}