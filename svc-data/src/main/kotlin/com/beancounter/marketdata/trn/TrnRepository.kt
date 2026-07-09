package com.beancounter.marketdata.trn

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.Optional

/**
 * CRUD Repo for business transactions.
 * Broker-related queries are in [TrnBrokerRepository].
 * Analysis and reporting queries are in [TrnAnalysisRepository].
 *
 * Queries returning Collection<Trn> use JOIN FETCH to avoid N+1 on
 * the ManyToOne associations that Hibernate would otherwise load individually.
 */
interface TrnRepository :
    CrudRepository<Trn, String>,
    TrnBrokerRepository,
    TrnAnalysisRepository {
    /**
     * Find transactions for position building. Only SETTLED transactions are included
     * in holdings calculations.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.portfolio.id = ?1 " +
            "and t.tradeDate <= ?2 " +
            "and t.status = ?3 " +
            "order by t.tradeDate, t.asset.code, t.createdAt"
    )
    fun findByPortfolioId(
        portfolioId: String,
        tradeDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Bulk delete of a portfolio's transactions. Issued as a single SQL
     * DELETE (not a select-then-remove) so it is idempotent: if the rows are
     * already gone — e.g. removed by the legacy `ON DELETE CASCADE` on
     * `trn.portfolio_id` when the portfolio itself is deleted — it simply
     * affects 0 rows instead of raising StaleStateException. See offboarding
     * (Sentry DATA-5P / DATA-5Q).
     */
    @Modifying
    @Query("DELETE FROM Trn t WHERE t.portfolio.id = :portfolioId")
    fun deleteByPortfolioId(
        @Param("portfolioId") portfolioId: String
    ): Long

    /**
     * Bulk delete of every transaction whose trade asset is [assetId].
     * Idempotent for the same reason as [deleteByPortfolioId]; cash-asset
     * references (a different column) are untouched — see [existsByCashAssetId].
     */
    @Modifying
    @Query("DELETE FROM Trn t WHERE t.asset.id = :assetId")
    fun deleteByAssetId(
        @Param("assetId") assetId: String
    ): Long

    /**
     * True if any transaction holds the asset as its trade asset.
     * Used by admin asset-delete to refuse deletion when the asset is
     * referenced by trades.
     */
    fun existsByAssetId(assetId: String): Boolean

    /**
     * True if any transaction uses the asset as its cash settlement asset.
     * Cash-asset references survive `deleteByAssetId` (they reference a
     * different column) and must be checked separately before delete.
     */
    fun existsByCashAssetId(assetId: String): Boolean

    @Modifying
    @Query("UPDATE Trn t SET t.cashAsset = null WHERE t.cashAsset.id = :assetId")
    fun clearCashAssetReferences(
        @Param("assetId") assetId: String
    ): Int

    fun findByPortfolioIdAndId(
        portfolioId: String,
        trnId: String
    ): Optional<Trn>

    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.portfolio.id = ?1 " +
            "and t.asset.id = ?2 " +
            "and t.trnType in (?3) " +
            "order by t.tradeDate desc, t.asset.code, t.createdAt desc"
    )
    fun findByPortfolioIdAndAssetIdAndTrnType(
        portfolioId: String,
        assetId: String,
        trnType: List<TrnType>
    ): Collection<Trn>

    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.portfolio.id = ?1 " +
            "and t.asset.id = ?2 " +
            "and t.tradeDate <= ?3 " +
            "order by t.tradeDate asc, t.createdAt asc"
    )
    fun findByPortfolioIdAndAssetIdUpTo(
        id: String,
        assetId: String,
        tradeDate: LocalDate
    ): Collection<Trn>

    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.portfolio.id = ?1 " +
            "and t.asset.id = ?2 " +
            "order by t.tradeDate asc, t.createdAt asc"
    )
    fun findByPortfolioIdAndAssetId(
        portfolioId: String,
        assetId: String
    ): Collection<Trn>

    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.portfolio.id = ?1 " +
            "and t.asset.id = ?2 " +
            "and t.trnType = ?3 " +
            "and t.tradeDate >= ?4 " +
            "and t.tradeDate <= ?5 " +
            "order by t.tradeDate asc, t.createdAt asc"
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
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.portfolio.id = ?1 " +
            "and t.status = ?2 " +
            "order by t.tradeDate desc, t.createdAt desc"
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
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.status = ?1 " +
            "and t.trnType in (?2) " +
            "and t.tradeDate <= ?3 " +
            "order by t.tradeDate asc, t.createdAt asc"
    )
    fun findDueEventTransactions(
        status: TrnStatus,
        eventTypes: List<TrnType>,
        tradeDate: LocalDate
    ): Collection<Trn>

    /**
     * Find transactions with a specific status for portfolios owned by the given user, bounded to
     * those whose tradeDate is on or before [asAt]. Used for the cross-portfolio proposed-review
     * view — `asAt` defaults to today at the controller, so not-yet-due (future-dated) proposed
     * transactions stay hidden until their date arrives.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.status = ?1 " +
            "and t.portfolio.owner = ?2 " +
            "and t.tradeDate <= ?3 " +
            "order by t.tradeDate desc, t.createdAt desc"
    )
    fun findByStatusAndPortfolioOwner(
        status: TrnStatus,
        owner: SystemUser,
        asAt: LocalDate
    ): Collection<Trn>

    /**
     * Count transactions with a specific status for portfolios owned by the given user, bounded to
     * those whose tradeDate is on or before [asAt].
     */
    @Query(
        "select count(t) from Trn t " +
            "where t.status = ?1 " +
            "and t.portfolio.owner = ?2 " +
            "and t.tradeDate <= ?3"
    )
    fun countByStatusAndPortfolioOwner(
        status: TrnStatus,
        owner: SystemUser,
        asAt: LocalDate
    ): Long

    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.status = ?1 " +
            "and t.portfolio.id in ?2 " +
            "and t.tradeDate <= ?3 " +
            "order by t.tradeDate desc, t.createdAt desc"
    )
    fun findByStatusAndPortfolioIdIn(
        status: TrnStatus,
        portfolioIds: Collection<String>,
        asAt: LocalDate
    ): Collection<Trn>

    @Query(
        "select count(t) from Trn t " +
            "where t.status = ?1 " +
            "and t.portfolio.id in ?2 " +
            "and t.tradeDate <= ?3"
    )
    fun countByStatusAndPortfolioIdIn(
        status: TrnStatus,
        portfolioIds: Collection<String>,
        asAt: LocalDate
    ): Long

    /**
     * Find all transactions with a specific status whose tradeDate falls within the
     * inclusive [from]..[to] window, for portfolios owned by the given user. Used by
     * the cross-portfolio Transactions review page, which filters by a date range.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.status = ?1 " +
            "and t.portfolio.owner = ?2 " +
            "and t.tradeDate >= ?3 " +
            "and t.tradeDate <= ?4 " +
            "order by t.portfolio.code, t.asset.code, t.createdAt"
    )
    fun findByStatusAndPortfolioOwnerAndTradeDateBetween(
        status: TrnStatus,
        owner: SystemUser,
        from: LocalDate,
        to: LocalDate
    ): Collection<Trn>

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
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.portfolio.id = ?1 " +
            "and (t.cashAsset.id = ?2 OR (t.asset.id = ?2 AND t.trnType = 'FX_BUY')) " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "order by t.tradeDate desc, t.createdAt desc"
    )
    fun findByPortfolioIdAndCashAssetId(
        portfolioId: String,
        cashAssetId: String,
        asAt: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find all transactions for a portfolio that belong to a specific rebalance model.
     * Used for model-level position tracking to show only quantities attributable to a model.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.portfolio.id = ?1 " +
            "and t.modelId = ?2 " +
            "and t.status = ?3 " +
            "order by t.tradeDate asc, t.createdAt asc"
    )
    fun findByPortfolioIdAndModelId(
        portfolioId: String,
        modelId: String,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find distinct asset IDs across the given portfolio IDs.
     * Used to identify which assets belong to specific portfolios for filtering.
     */
    @Query(
        "select distinct t.asset.id from Trn t " +
            "where t.portfolio.id in (?1)"
    )
    fun findDistinctAssetIdsByPortfolioIds(portfolioIds: Collection<String>): Collection<String>

    /**
     * Find sibling transactions grouped under the same caller_ref provider + batch.
     * Used by auto-settle to find the WITHDRAWAL + DEPOSIT pair it emitted for a
     * given parent trade (parent.callerId stamped as the batch on the children).
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.callerRef.provider = :provider " +
            "and t.callerRef.batch = :batch"
    )
    fun findByCallerRefProviderAndCallerRefBatch(
        @Param("provider") provider: String,
        @Param("batch") batch: String
    ): List<Trn>

    /**
     * PROPOSED trigger-type trades that carry no auto-settle (BC-AUTO) cash legs.
     * Feeds the one-off backfill that emits the compensating pair for trades
     * created / unsettled before leg status mirrored the parent. Trades without a
     * funding link or with a zero cash amount are pruned downstream by
     * [com.beancounter.marketdata.cash.CashAutoSettleService] (it no-ops), so this
     * only needs to exclude trades that already have legs.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.status = com.beancounter.common.model.TrnStatus.PROPOSED " +
            "and t.trnType in (:types) " +
            "and not exists (" +
            "select 1 from Trn l " +
            "where l.callerRef.provider = 'BC-AUTO' " +
            "and l.callerRef.batch = t.callerRef.callerId" +
            ")"
    )
    fun findProposedMissingAutoSettleLegs(
        @Param("types") types: Collection<TrnType>
    ): List<Trn>

    /**
     * Earliest SETTLED tradeDate for this asset across every portfolio that has ever held it.
     * Anchors price backfill: if portfolio A imported trades since 2020 and portfolio B since 2010,
     * backfill must reach 2010 for the wealth-performance "ALL" chart to be drawable.
     */
    @Query(
        "select min(t.tradeDate) from Trn t " +
            "where t.asset.id = :assetId " +
            "and t.status = com.beancounter.common.model.TrnStatus.SETTLED"
    )
    fun findEarliestTradeDateByAssetId(
        @Param("assetId") assetId: String
    ): LocalDate?

    /**
     * Group transaction count by [com.beancounter.common.model.TrnType].
     * Surfaced as the `beancounter.transaction.count.by_type` MultiGauge.
     */
    @Query(
        "SELECT new com.beancounter.marketdata.metrics.TypeCount(CAST(t.trnType AS string), COUNT(t)) " +
            "FROM Trn t GROUP BY t.trnType ORDER BY COUNT(t) DESC"
    )
    fun countByTrnType(): List<com.beancounter.marketdata.metrics.TypeCount>
}