package com.beancounter.position.cache

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "performance_snapshot",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_portfolio_date",
            columnNames = ["portfolio_id", "valuation_date"]
        )
    ]
)
data class PerformanceSnapshotEntity(
    @Id
    val id: String = KeyGenUtils().id,
    @Column(name = "portfolio_id", nullable = false, length = 36)
    val portfolioId: String,
    @Column(name = "valuation_date", nullable = false)
    val valuationDate: LocalDate,
    @Column(name = "market_value", nullable = false, precision = 19, scale = 4)
    val marketValue: BigDecimal,
    @Column(name = "external_cash_flow", nullable = false, precision = 19, scale = 4)
    val externalCashFlow: BigDecimal = BigDecimal.ZERO,
    @Column(name = "net_contributions", nullable = false, precision = 19, scale = 4)
    val netContributions: BigDecimal = BigDecimal.ZERO,
    @Column(name = "cumulative_dividends", nullable = false, precision = 19, scale = 4)
    val cumulativeDividends: BigDecimal = BigDecimal.ZERO,
    // No default: this is a JPA entity with no DI, so it cannot resolve "now" in the
    // configured beancounter.zone itself. The one caller (JpaPerformanceCacheService)
    // passes an explicit LocalDateTime.now(dateUtils.zoneId).
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime
)