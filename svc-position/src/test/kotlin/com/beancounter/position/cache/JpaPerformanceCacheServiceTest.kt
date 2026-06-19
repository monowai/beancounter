package com.beancounter.position.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate

@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JpaPerformanceCacheService::class]
        )
    ],
    properties = [
        "spring.main.allow-bean-definition-overriding=true"
    ]
)
@ActiveProfiles("test")
class JpaPerformanceCacheServiceTest {
    @Autowired
    private lateinit var cacheService: JpaPerformanceCacheService

    @Autowired
    private lateinit var repository: PerformanceSnapshotRepository

    private val portfolioId = "test-portfolio"
    private val date1 = LocalDate.of(2024, 1, 1)
    private val date2 = LocalDate.of(2024, 2, 1)
    private val date3 = LocalDate.of(2024, 3, 1)

    @BeforeEach
    fun cleanup() {
        repository.deleteAll()
    }

    @Test
    fun `store and retrieve snapshots`() {
        val snapshots =
            listOf(
                CachedSnapshot(
                    valuationDate = date1,
                    marketValue = BigDecimal("10000.00"),
                    externalCashFlow = BigDecimal("5000.00"),
                    netContributions = BigDecimal("5000.00"),
                    cumulativeDividends = BigDecimal.ZERO
                ),
                CachedSnapshot(
                    valuationDate = date2,
                    marketValue = BigDecimal("11000.00"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("5000.00"),
                    cumulativeDividends = BigDecimal("200.00")
                )
            )

        cacheService.storeSnapshots(portfolioId, snapshots)
        val result = cacheService.findSnapshots(portfolioId, listOf(date1, date2))

        assertThat(result).hasSize(2)
        val byDate = result.associateBy { it.valuationDate }
        assertThat(byDate[date1]?.marketValue).isEqualByComparingTo(BigDecimal("10000.00"))
        assertThat(byDate[date2]?.cumulativeDividends).isEqualByComparingTo(BigDecimal("200.00"))
    }

    @Test
    fun `find returns empty for missing dates`() {
        val result = cacheService.findSnapshots(portfolioId, listOf(date1))
        assertThat(result).isEmpty()
    }

    @Test
    fun `invalidateFrom deletes snapshots on or after date`() {
        val snapshots =
            listOf(
                CachedSnapshot(date1, BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                CachedSnapshot(date2, BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                CachedSnapshot(date3, BigDecimal("300"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
            )
        cacheService.storeSnapshots(portfolioId, snapshots)

        cacheService.invalidateFrom(portfolioId, date2)

        val remaining = cacheService.findSnapshots(portfolioId, listOf(date1, date2, date3))
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].valuationDate).isEqualTo(date1)
    }

    @Test
    fun `invalidateOnDate deletes all portfolios for that date`() {
        cacheService.storeSnapshots(
            "pf-1",
            listOf(CachedSnapshot(date1, BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
        )
        cacheService.storeSnapshots(
            "pf-2",
            listOf(CachedSnapshot(date1, BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
        )

        cacheService.invalidateOnDate(date1)

        assertThat(cacheService.findSnapshots("pf-1", listOf(date1))).isEmpty()
        assertThat(cacheService.findSnapshots("pf-2", listOf(date1))).isEmpty()
    }

    @Test
    fun `invalidateFromDate deletes all rows on-or-after the date across portfolios`() {
        cacheService.storeSnapshots(
            "pf-1",
            listOf(
                CachedSnapshot(date1, BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                CachedSnapshot(date2, BigDecimal("110"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
            )
        )
        cacheService.storeSnapshots(
            "pf-2",
            listOf(
                CachedSnapshot(date1, BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                CachedSnapshot(date2, BigDecimal("220"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
            )
        )

        // Wipe everything from date2 onward in both portfolios; date1 must survive.
        cacheService.invalidateFromDate(date2)

        val remainingPf1 = cacheService.findSnapshots("pf-1", listOf(date1, date2))
        val remainingPf2 = cacheService.findSnapshots("pf-2", listOf(date1, date2))
        assertThat(remainingPf1).hasSize(1)
        assertThat(remainingPf1[0].valuationDate).isEqualTo(date1)
        assertThat(remainingPf2).hasSize(1)
        assertThat(remainingPf2[0].valuationDate).isEqualTo(date1)
    }

    @Test
    fun `invalidatePortfolio deletes all dates for that portfolio`() {
        cacheService.storeSnapshots(
            portfolioId,
            listOf(
                CachedSnapshot(date1, BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                CachedSnapshot(date2, BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
            )
        )

        cacheService.invalidatePortfolio(portfolioId)

        assertThat(cacheService.findSnapshots(portfolioId, listOf(date1, date2))).isEmpty()
    }

    @Test
    fun `isAvailable returns true`() {
        assertThat(cacheService.isAvailable()).isTrue()
    }

    @Test
    fun `storeSnapshots upserts existing rows without violating unique constraint`() {
        // Regression for POSITION-2Q: storing the same (portfolio, date) twice
        // must update in place, not throw on uk_portfolio_date.
        val first =
            CachedSnapshot(
                valuationDate = date1,
                marketValue = BigDecimal("100.00"),
                externalCashFlow = BigDecimal.ZERO,
                netContributions = BigDecimal.ZERO,
                cumulativeDividends = BigDecimal.ZERO
            )
        cacheService.storeSnapshots(portfolioId, listOf(first))

        val updated =
            CachedSnapshot(
                valuationDate = date1,
                marketValue = BigDecimal("250.00"),
                externalCashFlow = BigDecimal("10.00"),
                netContributions = BigDecimal("5.00"),
                cumulativeDividends = BigDecimal("1.00")
            )
        cacheService.storeSnapshots(portfolioId, listOf(updated))

        val result = cacheService.findSnapshots(portfolioId, listOf(date1))
        assertThat(result).hasSize(1)
        assertThat(result[0].marketValue).isEqualByComparingTo(BigDecimal("250.00"))
        assertThat(result[0].externalCashFlow).isEqualByComparingTo(BigDecimal("10.00"))
        assertThat(result[0].cumulativeDividends).isEqualByComparingTo(BigDecimal("1.00"))
    }
}