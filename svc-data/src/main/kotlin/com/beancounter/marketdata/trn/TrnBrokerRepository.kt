package com.beancounter.marketdata.trn

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.NoRepositoryBean
import java.time.LocalDate

/**
 * Broker-related transaction queries. Extended by [TrnRepository].
 *
 * Queries use JOIN FETCH to avoid N+1 on ManyToOne associations.
 */
@NoRepositoryBean
interface TrnBrokerRepository {
    companion object {
        /** JPQL IN-list of market codes that are never broker-tradeable (private/cash assets). */
        private const val EXCLUDED_MARKET_CODES = "'PRIVATE', 'CASH'"
    }

    /**
     * Find settled trade transactions for a specific broker across all portfolios owned by the user.
     * Only includes BUY, SELL, ADD, REDUCE transaction types for broker reconciliation.
     * Excludes PRIVATE market assets as they don't have brokers.
     * Excludes cash assets (CASH, ACCOUNT, TRADE, BANK ACCOUNT categories).
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.broker.id = ?1 " +
            "and t.portfolio.owner = ?2 " +
            "and t.status = ?3 " +
            "and t.trnType in ('BUY', 'SELL', 'ADD', 'REDUCE') " +
            "and t.asset.marketCode not in (" + EXCLUDED_MARKET_CODES + ") " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.asset.code, t.tradeDate, t.createdAt"
    )
    fun findByBrokerIdAndOwner(
        brokerId: String,
        owner: SystemUser,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find settled trades (BUY/SELL/ADD/REDUCE etc.) for a specific broker for position
     * building, up to a given date. Note: SPLIT corporate actions carry no broker and
     * are NOT returned here — callers must also fetch [findBrokerSplits] and merge, or
     * the Accumulator builds split-unadjusted (negative) quantities.
     * Excludes PRIVATE market assets as they don't have brokers.
     * Excludes cash assets (CASH, ACCOUNT, TRADE, BANK ACCOUNT categories).
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.broker.id = ?1 " +
            "and t.portfolio.owner = ?2 " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "and t.asset.marketCode not in (" + EXCLUDED_MARKET_CODES + ") " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.tradeDate, t.asset.code, t.createdAt"
    )
    fun findAllByBrokerIdForPositions(
        brokerId: String,
        owner: SystemUser,
        tradeDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find broker-null SPLIT corporate actions that apply to the (portfolio, asset)
     * combinations a broker actually trades. Split rows carry no broker, so the
     * broker-scoped queries silently drop them — leaving a sold-out holding negative
     * because the split that grew it was never applied. The EXISTS clause scopes each
     * split to a portfolio+asset where this broker holds the asset, so a split in an
     * unrelated portfolio is not pulled in.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.broker is null " +
            "and t.trnType = 'SPLIT' " +
            "and t.portfolio.owner = ?2 " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "and exists (" +
            "select 1 from Trn b " +
            "where b.broker.id = ?1 " +
            "and b.portfolio = t.portfolio " +
            "and b.asset = t.asset" +
            ") " +
            "order by t.tradeDate, t.asset.code, t.createdAt"
    )
    fun findBrokerSplits(
        brokerId: String,
        owner: SystemUser,
        tradeDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find settled trade transactions with no broker assigned across all portfolios owned by the user.
     * Used to identify transactions that need a broker assignment.
     * Excludes PRIVATE market assets as they don't have brokers.
     * Excludes cash assets (CASH, ACCOUNT, TRADE, BANK ACCOUNT categories).
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.broker is null " +
            "and t.portfolio.owner = ?1 " +
            "and t.status = ?2 " +
            "and t.trnType in ('BUY', 'SELL', 'ADD', 'REDUCE') " +
            "and t.asset.marketCode not in (" + EXCLUDED_MARKET_CODES + ") " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.asset.code, t.tradeDate, t.createdAt"
    )
    fun findWithNoBroker(
        owner: SystemUser,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find ALL settled transactions with no broker assigned for position building.
     * Includes all transaction types (BUY, SELL, SPLIT, DIVI, etc.) up to a given date.
     * Used by svc-position to build holdings with correct split-adjusted quantities.
     * Excludes PRIVATE and CASH market assets.
     * Excludes cash assets (CASH, ACCOUNT, TRADE, BANK ACCOUNT categories).
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.broker is null " +
            "and t.portfolio.owner = ?1 " +
            "and t.tradeDate <= ?2 " +
            "and t.status = ?3 " +
            "and t.asset.marketCode not in (" + EXCLUDED_MARKET_CODES + ") " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.tradeDate, t.asset.code, t.createdAt"
    )
    fun findAllWithNoBrokerForPositions(
        owner: SystemUser,
        tradeDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * True when the owner already has a transaction of the given type/status
     * for this asset at this broker (any portfolio). Used to block duplicate
     * weighted sell proposals from the reconciliation view.
     */
    @Query(
        "select count(t) > 0 from Trn t " +
            "where t.broker.id = ?1 " +
            "and t.asset.id = ?2 " +
            "and t.trnType = ?3 " +
            "and t.status = ?4 " +
            "and t.portfolio.owner = ?5"
    )
    fun existsByBrokerAndAssetAndTypeAndStatus(
        brokerId: String,
        assetId: String,
        trnType: TrnType,
        status: TrnStatus,
        owner: SystemUser
    ): Boolean

    /**
     * Count transactions for a specific broker owned by the user.
     * Used to check if a broker can be deleted.
     */
    @Query(
        "select count(t) from Trn t " +
            "where t.broker.id = ?1 " +
            "and t.portfolio.owner = ?2"
    )
    fun countByBrokerId(
        brokerId: String,
        owner: SystemUser
    ): Long

    /**
     * Find all transactions for a specific broker owned by the user.
     * Used for transferring transactions to another broker.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.broker.id = ?1 " +
            "and t.portfolio.owner = ?2"
    )
    fun findAllByBrokerId(
        brokerId: String,
        owner: SystemUser
    ): Collection<Trn>

    /**
     * Find settled trade transactions for a specific asset across every broker, for
     * portfolios owned by the user. Reverse of [findByBrokerIdAndOwner] — groups by
     * broker for the asset-brokers lookup (which broker(s) hold this asset).
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "join fetch t.broker " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.asset.id = ?1 " +
            "and t.broker is not null " +
            "and t.portfolio.owner = ?2 " +
            "and t.status = ?3 " +
            "and t.trnType in ('BUY', 'SELL', 'ADD', 'REDUCE') " +
            "and t.asset.marketCode not in (" + EXCLUDED_MARKET_CODES + ") " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.broker.id, t.portfolio.code, t.tradeDate, t.createdAt"
    )
    fun findByAssetAndOwner(
        assetId: String,
        owner: SystemUser,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find settled trade transactions for a specific asset with no broker assigned,
     * for portfolios owned by the user. Reverse of [findWithNoBroker] — feeds the
     * [TrnBrokerService.NO_BROKER] group of the asset-brokers lookup.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.asset.id = ?1 " +
            "and t.broker is null " +
            "and t.portfolio.owner = ?2 " +
            "and t.status = ?3 " +
            "and t.trnType in ('BUY', 'SELL', 'ADD', 'REDUCE') " +
            "and t.asset.marketCode not in (" + EXCLUDED_MARKET_CODES + ") " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.portfolio.code, t.tradeDate, t.createdAt"
    )
    fun findWithNoBrokerByAssetAndOwner(
        assetId: String,
        owner: SystemUser,
        status: TrnStatus
    ): Collection<Trn>

    /**
     * Find broker-null SPLIT corporate actions for a specific asset, for portfolios
     * owned by the user, up to the given date. Splits carry no broker so this is
     * merged into whichever broker/NO_BROKER group the same portfolio's trades fall
     * under — mirrors [findBrokerSplits] but scoped by asset instead of broker.
     */
    @Query(
        "select t from Trn t " +
            "join fetch t.asset " +
            "join fetch t.tradeCurrency " +
            "join fetch t.portfolio " +
            "left join fetch t.cashAsset " +
            "left join fetch t.cashCurrency " +
            "where t.asset.id = ?1 " +
            "and t.broker is null " +
            "and t.trnType = 'SPLIT' " +
            "and t.portfolio.owner = ?2 " +
            "and t.tradeDate <= ?3 " +
            "and t.status = ?4 " +
            "order by t.portfolio.code, t.tradeDate, t.createdAt"
    )
    fun findSplitsByAssetAndOwner(
        assetId: String,
        owner: SystemUser,
        tradeDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>
}