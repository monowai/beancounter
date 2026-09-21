package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
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
 * `MarketDataPriceProcessor`'s market-closed fallback used to call
 * `PriceService.getLatestMarketData(asset, date)` once PER remaining asset - Sentry
 * flagged 18 repetitions of `findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc`
 * for an 18-asset request. The batched implementation resolves the whole asset set in one
 * query regardless of how many assets are requested.
 *
 * Mock-call-count exception (per SERVICE_DESIGN.md TDD guidance): Hibernate statistics
 * measure actual SQL query volume, not a mock's invocation count, so this verifies the
 * real DB-roundtrip behaviour the Sentry issue was about.
 */
@SpringMvcDbTest
class PriceServiceLatestMarketDataBatchTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var priceService: PriceService

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var marketDataRepo: MarketDataRepo

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `getLatestMarketData resolves multiple assets in one query, not one per asset`() {
        val asOf = LocalDate.of(2024, 12, 25)
        val lastTradingDay = LocalDate.of(2024, 12, 24)
        val assets =
            (1..5).map { i ->
                assetRepository.save(
                    Asset(
                        code = "DATA6G-$i",
                        market = NASDAQ,
                        marketCode = NASDAQ.code
                    )
                )
            }
        assets.forEach { asset ->
            marketDataRepo.save(
                MarketData(
                    asset = asset,
                    priceDate = lastTradingDay,
                    close = BigDecimal("10.00")
                )
            )
        }

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()

        val result = priceService.getLatestMarketData(assets, asOf)

        assertThat(result).hasSize(5)
        assets.forEach { asset ->
            assertThat(result[asset.id]?.close).isEqualByComparingTo(BigDecimal("10.00"))
        }
        assertThat(statistics.queryExecutionCount)
            .describedAs(
                "query count must be bounded, not O(assets) - was %d for %d assets",
                statistics.queryExecutionCount,
                assets.size
            ).isEqualTo(1L)
    }

    @Test
    fun `getLatestMarketData omits assets with no prior price rather than erroring`() {
        val asOf = LocalDate.of(2024, 12, 25)
        val priced =
            assetRepository.save(
                Asset(
                    code = "DATA6G-PRICED",
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        val neverPriced =
            assetRepository.save(
                Asset(
                    code = "DATA6G-UNPRICED",
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        marketDataRepo.save(
            MarketData(
                asset = priced,
                priceDate = asOf.minusDays(1),
                close = BigDecimal("20.00")
            )
        )

        val result = priceService.getLatestMarketData(listOf(priced, neverPriced), asOf)

        assertThat(result).containsOnlyKeys(priced.id)
        assertThat(result[priced.id]?.close).isEqualByComparingTo(BigDecimal("20.00"))
    }

    @Test
    fun `getLatestMarketData returns an empty map for an empty asset collection`() {
        val result = priceService.getLatestMarketData(emptyList(), LocalDate.of(2024, 12, 25))

        assertThat(result).isEmpty()
    }
}