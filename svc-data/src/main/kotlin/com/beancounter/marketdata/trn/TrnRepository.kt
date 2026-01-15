package com.beancounter.marketdata.trn

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.util.Optional

/**
 * CRUD Repo for business transactions.
 */
interface TrnRepository : CrudRepository<Trn, String> {
    /**
     * Find transactions for position building. Only SETTLED transactions are included
     * in holdings calculations.
     */
    @Query(
        "select t from Trn t " +
            "where t.portfolio.id =?1  " +
            "and t.tradeDate <= ?2 " +
            "and t.status = ?3"
    )
    fun findByPortfolioId(
        portfolioId: String,
        tradeDate: LocalDate,
        status: TrnStatus,
        sort: Sort
    ): Collection<Trn>

    fun deleteByPortfolioId(portfolioId: String): Long

    fun findByPortfolioIdAndId(
        portfolioId: String,
        trnId: String
    ): Optional<Trn>

    @Query(
        "select t from Trn t " +
            "where t.portfolio.id =?1 " +
            "and t.asset.id = ?2 " +
            "and t.trnType in (?3) "
    )
    fun findByPortfolioIdAndAssetIdAndTrnType(
        portfolioId: String,
        assetId: String,
        trnType: List<TrnType>,
        sort: Sort
    ): Collection<Trn>

    @Query(
        "select t from Trn t " +
            "where t.portfolio.id =?1 " +
            "and t.asset.id = ?2 " +
            "and t.tradeDate <= ?3 " +
            "order by t.tradeDate asc "
    )
    fun findByPortfolioIdAndAssetIdUpTo(
        id: String,
        assetId: String,
        tradeDate: LocalDate
    ): Collection<Trn>

    @Query(
        "select t from Trn t  " +
            "where t.portfolio.id =?1  " +
            "and t.asset.id =?2 " +
            "and t.trnType = ?3 " +
            "and t.tradeDate >= ?4 " +
            "and t.tradeDate <= ?5 " +
            "order by t.tradeDate asc "
    )
    fun findExisting(
        portfolio: String,
        asset: String,
        trnType: TrnType,
        tradeDate: LocalDate,
        endDate: LocalDate
    ): Collection<Trn>

    /**
     * Find transactions for a portfolio with a specific status.
     */
    @Query(
        "select t from Trn t " +
            "where t.portfolio.id = ?1 " +
            "and t.status = ?2 " +
            "order by t.tradeDate desc"
    )
    fun findByPortfolioIdAndStatus(
        portfolioId: String,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find EVENT transactions (DIVI, SPLIT) with a specific status where tradeDate
     * is on or before the given date. Used by the auto-settle scheduler to find
     * PROPOSED event transactions that are due for settlement.
     * TRADE transactions (BUY, SELL, etc.) are NOT included.
     */
    @Query(
        "select t from Trn t " +
            "where t.status = ?1 " +
            "and t.trnType in (?2) " +
            "and t.tradeDate <= ?3 " +
            "order by t.tradeDate asc"
    )
    fun findDueEventTransactions(
        status: TrnStatus,
        eventTypes: List<TrnType>,
        tradeDate: LocalDate
    ): Collection<Trn>

    /**
     * Find all transactions with a specific status for portfolios owned by the given user.
     * Used for cross-portfolio proposed transaction views.
     */
    @Query(
        "select t from Trn t " +
            "where t.status = ?1 " +
            "and t.portfolio.owner = ?2 " +
            "order by t.tradeDate desc"
    )
    fun findByStatusAndPortfolioOwner(
        status: TrnStatus,
        owner: SystemUser
    ): Collection<Trn>

    /**
     * Count all transactions with a specific status for portfolios owned by the given user.
     */
    @Query(
        "select count(t) from Trn t " +
            "where t.status = ?1 " +
            "and t.portfolio.owner = ?2"
    )
    fun countByStatusAndPortfolioOwner(
        status: TrnStatus,
        owner: SystemUser
    ): Long

    /**
     * Find all SETTLED transactions for a portfolio where the cash settlement asset matches
     * and trade date is on or before the specified date.
     * This is used for the Cash Ladder feature to show all transactions that
     * impacted a specific cash position (account).
     *
     * Includes:
     * - Transactions where cashAsset matches (BUY, SELL, DIVI, DEPOSIT, WITHDRAWAL, etc.)
     * - FX_BUY transactions where asset matches (the purchased currency)
     */
    @Query(
        "select t from Trn t " +
            "where t.portfolio.id = ?1 " +
            "and (t.cashAsset.id = ?2 OR (t.asset.id = ?2 AND t.trnType = 'FX_BUY')) " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "order by t.tradeDate desc"
    )
    fun findByPortfolioIdAndCashAssetId(
        portfolioId: String,
        cashAssetId: String,
        asAt: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

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
}