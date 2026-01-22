package com.beancounter.marketdata.broker

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/**
 * Repository for broker settlement account mappings.
 */
interface BrokerSettlementAccountRepository : JpaRepository<BrokerSettlementAccount, String> {
    fun findByBrokerId(brokerId: String): List<BrokerSettlementAccount>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BrokerSettlementAccount b WHERE b.broker.id = :brokerId")
    fun deleteByBrokerId(brokerId: String)
}