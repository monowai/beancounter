package com.beancounter.marketdata.trn

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
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
            "and t.asset.marketCode not in ('PRIVATE', 'CASH') " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.asset.code, t.tradeDate"
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
            "and t.asset.marketCode not in ('PRIVATE', 'CASH') " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.tradeDate, t.asset.code"
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
            "order by t.tradeDate, t.asset.code"
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
            "and t.asset.marketCode not in ('PRIVATE', 'CASH') " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.asset.code, t.tradeDate"
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
            "and t.asset.marketCode not in ('PRIVATE', 'CASH') " +
            "and t.asset.category not in ('CASH', 'ACCOUNT', 'TRADE', 'BANK ACCOUNT') " +
            "order by t.tradeDate, t.asset.code"
    )
    fun findAllWithNoBrokerForPositions(
        owner: SystemUser,
        tradeDate: LocalDate,
        status: TrnStatus
    ): Collection<Trn>

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
}