package com.beancounter.position.cache

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface PerformanceSnapshotRepository : JpaRepository<PerformanceSnapshotEntity, String> {
    fun findByPortfolioIdOrderByValuationDate(portfolioId: String): List<PerformanceSnapshotEntity>

    fun findByPortfolioIdAndValuationDateIn(
        portfolioId: String,
        dates: Collection<LocalDate>
    ): List<PerformanceSnapshotEntity>

    @Modifying
    @Query(
        "DELETE FROM PerformanceSnapshotEntity e " +
            "WHERE e.portfolioId = :portfolioId AND e.valuationDate >= :fromDate"
    )
    fun deleteByPortfolioIdAndValuationDateGreaterThanEqual(
        portfolioId: String,
        fromDate: LocalDate
    )

    @Modifying
    fun deleteByPortfolioIdAndValuationDateIn(
        portfolioId: String,
        dates: Collection<LocalDate>
    )

    @Modifying
    fun deleteByValuationDate(date: LocalDate)

    @Modifying
    fun deleteByPortfolioId(portfolioId: String)
}