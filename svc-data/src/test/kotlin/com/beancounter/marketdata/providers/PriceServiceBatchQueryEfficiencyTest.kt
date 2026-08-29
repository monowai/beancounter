package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
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
 * Query-efficiency regression guard for #1096. `PriceService.handle` used to
 * issue a `countByAssetIdAndPriceDate` + `findTop1By...LessThan` pair PER ROW
 * — a 1y/~260-row backfill meant 500+ queries, and a 116-asset backfill storm
 * OOM'd bc-data on a 512m heap. The batched implementation loads one asset
 * group's pre-existing stored state in two queries regardless of row count.
 *
 * This is a mock-call-count exception (per SERVICE_DESIGN.md TDD guidance):
 * Hibernate statistics measure actual SQL query volume, not a mock's
 * invocation count, so it verifies the real allocation/DB-roundtrip
 * behaviour the incident was about.
 */
@SpringMvcDbTest
class PriceServiceBatchQueryEfficiencyTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var priceService: PriceService

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `handle issues a bounded number of queries regardless of row count`() {
        val asset =
            assetRepository.save(
                Asset(
                    code = "QEFF-BATCH",
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        val rows =
            (1..50).map { day ->
                MarketData(
                    asset = asset,
                    priceDate = LocalDate.of(2024, 1, 1).plusDays(day.toLong()),
                    close = BigDecimal(100 + day)
                )
            }

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()

        val saved = priceService.handle(PriceResponse(rows))

        assertThat(saved.count()).isEqualTo(50)
        assertThat(statistics.queryExecutionCount)
            .describedAs(
                "query count must be bounded, not O(rows) — was %d for %d rows",
                statistics.queryExecutionCount,
                rows.size
            ).isLessThanOrEqualTo(6L)
    }
}