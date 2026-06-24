package com.beancounter.marketdata.broker

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Repository for broker settlement account mappings.
 */
interface BrokerSettlementAccountRepository : JpaRepository<BrokerSettlementAccount, String> {
    @Query(
        "SELECT b FROM BrokerSettlementAccount b " +
            "JOIN FETCH b.broker JOIN FETCH b.account " +
            "WHERE b.broker.id = :brokerId"
    )
    fun findByBrokerId(
        @Param("brokerId") brokerId: String
    ): List<BrokerSettlementAccount>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BrokerSettlementAccount b WHERE b.broker.id = :brokerId")
    fun deleteByBrokerId(brokerId: String)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BrokerSettlementAccount b WHERE b.account.id = :accountId")
    fun deleteByAccountId(accountId: String)

    /**
     * Bulk idempotent delete of all settlement accounts for brokers owned by [ownerId].
     * Must be called before [BrokerRepository.deleteByOwnerId] to satisfy the
     * broker_settlement_account.broker_id FK constraint.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BrokerSettlementAccount b WHERE b.broker.owner.id = :ownerId")
    fun deleteByBrokerOwnerId(
        @Param("ownerId") ownerId: String
    )
}