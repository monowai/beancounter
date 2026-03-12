package com.beancounter.marketdata.trn

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.NoRepositoryBean
import java.time.LocalDate

/**
 * Analysis and reporting transaction queries. Extended by [TrnRepository].
 */
@NoRepositoryBean
interface TrnAnalysisRepository {
    /**
     * Sum net investment amount for a user within a date range.
     * Calculates: BUY - SELL to show net new capital invested.
     * ADD transactions are excluded as they represent transfers, not new investment.
     * Used for tracking monthly investment progress against goals.
     */
    @Query(
        "select coalesce(sum(case when t.trnType = 'BUY' then t.tradeAmount " +
            "when t.trnType = 'SELL' then -t.tradeAmount else 0 end), 0) from Trn t " +
            "where t.portfolio.owner = ?1 " +
            "and t.trnType in ('BUY', 'SELL') " +
            "and t.tradeDate >= ?2 " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4"
    )
    fun sumInvestmentsByOwnerAndDateRange(
        owner: SystemUser,
        startDate: LocalDate,
        endDate: LocalDate,
        status: TrnStatus
    ): java.math.BigDecimal

    /**
     * Find all investment-related transactions (BUY, SELL) for a user within a date range.
     * ADD transactions are excluded as they represent transfers, not new investment.
     * Returns individual transactions for detailed breakdown.
     */
    @Query(
        "select t from Trn t " +
            "where t.portfolio.owner = ?1 " +
            "and t.trnType in ('BUY', 'SELL') " +
            "and t.tradeDate >= ?2 " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "order by t.tradeDate desc"
    )
    fun findInvestmentsByOwnerAndDateRange(
        owner: SystemUser,
        startDate: LocalDate,
        endDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find investment transactions for specific portfolios within a date range.
     * ADD transactions are excluded as they represent transfers, not new investment.
     * Used when scoping to an independence plan's portfolios.
     */
    @Query(
        "select t from Trn t " +
            "where t.portfolio.id in ?1 " +
            "and t.trnType in ('BUY', 'SELL') " +
            "and t.tradeDate >= ?2 " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "order by t.tradeDate desc"
    )
    fun findInvestmentsByPortfoliosAndDateRange(
        portfolioIds: List<String>,
        startDate: LocalDate,
        endDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find income transactions (DIVI) for a user within a date range.
     * Used for monthly income reports.
     */
    @Query(
        "select t from Trn t " +
            "where t.portfolio.owner = ?1 " +
            "and t.trnType = 'DIVI' " +
            "and t.tradeDate >= ?2 " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "order by t.tradeDate desc"
    )
    fun findIncomeByOwnerAndDateRange(
        owner: SystemUser,
        startDate: LocalDate,
        endDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find income transactions (DIVI) for specific portfolios within a date range.
     * Used for monthly income reports scoped to specific portfolios.
     */
    @Query(
        "select t from Trn t " +
            "where t.portfolio.id in ?1 " +
            "and t.trnType = 'DIVI' " +
            "and t.tradeDate >= ?2 " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "order by t.tradeDate desc"
    )
    fun findIncomeByPortfoliosAndDateRange(
        portfolioIds: List<String>,
        startDate: LocalDate,
        endDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>
}