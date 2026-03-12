package com.beancounter.marketdata.trn

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.NoRepositoryBean
import java.time.LocalDate

/**
 * Broker-related transaction queries. Extended by [TrnRepository].
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
     * Find ALL settled transactions for a specific broker for position building.
     * Includes all transaction types (BUY, SELL, SPLIT, DIVI, etc.) up to a given date.
     * Used by svc-position to build holdings with correct split-adjusted quantities.
     * Excludes PRIVATE market assets as they don't have brokers.
     * Excludes cash assets (CASH, ACCOUNT, TRADE, BANK ACCOUNT categories).
     */
    @Query(
        "select t from Trn t " +
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
     * Find settled trade transactions with no broker assigned across all portfolios owned by the user.
     * Used to identify transactions that need a broker assignment.
     * Excludes PRIVATE market assets as they don't have brokers.
     * Excludes cash assets (CASH, ACCOUNT, TRADE, BANK ACCOUNT categories).
     */
    @Query(
        "select t from Trn t " +
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
            "where t.broker.id = ?1 " +
            "and t.portfolio.owner = ?2"
    )
    fun findAllByBrokerId(
        brokerId: String,
        owner: SystemUser
    ): Collection<Trn>
}