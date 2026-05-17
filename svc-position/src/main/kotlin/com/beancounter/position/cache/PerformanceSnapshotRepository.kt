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

    /**
     * Delete all performance snapshot entities whose valuation date equals the provided date.
     *
     * @param date The valuation date identifying snapshots to remove.
     */
    @Modifying
    fun deleteByValuationDate(date: LocalDate)

    /**
     * Deletes all PerformanceSnapshotEntity records whose valuationDate is greater than or equal to the provided date.
     *
     * @param fromDate The inclusive lower bound for valuationDate; records with valuationDate >= fromDate will be removed.
     */
    @Modifying
    @Query("DELETE FROM PerformanceSnapshotEntity e WHERE e.valuationDate >= :fromDate")
    fun deleteByValuationDateGreaterThanEqual(fromDate: LocalDate)

    /**
     * Deletes all performance snapshot rows belonging to the specified portfolio.
     *
     * @param portfolioId Identifier of the portfolio whose performance snapshots will be removed.
     */
    @Modifying
    fun deleteByPortfolioId(portfolioId: String)
}