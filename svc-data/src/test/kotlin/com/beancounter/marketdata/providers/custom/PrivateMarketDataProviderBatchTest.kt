package com.beancounter.marketdata.providers.custom

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.providers.MarketDataRepo
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Query-efficiency regression guard for DATA-6G (Sentry N+1 on `POST /api/prices`).
 * `PrivateMarketDataProvider.getMarketData(priceRequest)` used to call
 * `MarketDataRepo.findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc` once
 * per PRIVATE-market asset in the request. Unlike the market-closed fallback fixed
 * alongside this in `MarketDataPriceProcessor`, this loop runs on EVERY
 * `POST /api/prices` call that includes a PRIVATE asset (real estate, art, accounts,
 * policies) - not just holidays - so it was the more frequent source of the N+1.
 *
 * Mock-call-count exception (per SERVICE_DESIGN.md TDD guidance): Hibernate statistics
 * measure actual SQL query volume, not a mock's invocation count.
 */
@SpringMvcDbTest
class PrivateMarketDataProviderBatchTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var provider: PrivateMarketDataProvider

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var marketDataRepo: MarketDataRepo

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    private val privateMarket = Market(PrivateMarketDataProvider.ID)

    @Test
    fun `getMarketData resolves multiple private assets in one query, not one per asset`() {
        val yesterday = LocalDate.now().minusDays(1)
        val assets =
            (1..4).map { i ->
                assetRepository.save(
                    Asset(
                        code = "DATA6G-PRIV-$i",
                        market = privateMarket,
                        marketCode = privateMarket.code,
                        category = AssetCategory.RE
                    )
                )
            }
        assets.forEach { asset ->
            marketDataRepo.save(
                MarketData(
                    asset = asset,
                    priceDate = yesterday,
                    close = BigDecimal("500.00")
                )
            )
        }

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()

        val priceRequest = PriceRequest(assets = assets.map { PriceAsset(it) })
        val result = provider.getMarketData(priceRequest)

        assertThat(result).hasSize(4)
        assertThat(result.map { it.close }).allMatch { it.compareTo(BigDecimal("500.00")) == 0 }
        assertThat(statistics.queryExecutionCount)
            .describedAs(
                "query count must be bounded, not O(assets) - was %d for %d assets",
                statistics.queryExecutionCount,
                assets.size
            ).isEqualTo(1L)
    }

    @Test
    fun `getMarketData uses the request default price for an asset with no stored row`() {
        val unpriced =
            assetRepository.save(
                Asset(
                    code = "DATA6G-PRIV-UNPRICED",
                    market = privateMarket,
                    marketCode = privateMarket.code,
                    category = AssetCategory.RE
                )
            )

        val priceRequest =
            PriceRequest(
                assets = listOf(PriceAsset(unpriced)),
                closePrice = BigDecimal("42.00")
            )
        val result = provider.getMarketData(priceRequest)

        assertThat(result).hasSize(1)
        assertThat(result.first().close).isEqualByComparingTo(BigDecimal("42.00"))
    }

    @Test
    fun `getMarketData still prices ACCOUNT assets at 1 without consulting stored rows`() {
        val account =
            assetRepository.save(
                Asset(
                    code = "DATA6G-ACCOUNT",
                    market = privateMarket,
                    marketCode = privateMarket.code,
                    category = AssetCategory.ACCOUNT
                )
            )

        val priceRequest = PriceRequest(assets = listOf(PriceAsset(account)))
        val result = provider.getMarketData(priceRequest)

        assertThat(result).hasSize(1)
        assertThat(result.first().close).isEqualByComparingTo(BigDecimal.ONE)
    }
}