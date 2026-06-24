package com.beancounter.marketdata.broker

import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BrokerRepository : JpaRepository<Broker, String> {
    @Query(
        "SELECT b FROM Broker b JOIN FETCH b.owner WHERE b.owner = :owner"
    )
    fun findByOwner(
        @Param("owner") owner: SystemUser
    ): List<Broker>

    @Query(
        "SELECT b FROM Broker b JOIN FETCH b.owner WHERE b.id = :id AND b.owner = :owner"
    )
    fun findByIdAndOwner(
        @Param("id") id: String,
        @Param("owner") owner: SystemUser
    ): Optional<Broker>

    @Query(
        "SELECT b FROM Broker b JOIN FETCH b.owner WHERE b.name = :name AND b.owner = :owner"
    )
    fun findByNameAndOwner(
        @Param("name") name: String,
        @Param("owner") owner: SystemUser
    ): Optional<Broker>

    /**
     * Bulk idempotent delete of every broker owned by [ownerId]. Must be called
     * AFTER [BrokerSettlementAccountRepository.deleteByBrokerOwnerId] to satisfy
     * the broker_settlement_account.broker_id FK. Safe to call when no rows
     * match (0-row delete, no exception) — concurrent offboarding calls cannot
     * trigger StaleObjectStateException. Mirrors the pattern in
     * TrnRepository.deleteByPortfolioId (Sentry DATA-5P / DATA-5Q).
     */
    @Modifying
    @Query("delete from Broker b where b.owner.id = :ownerId")
    fun deleteByOwnerId(
        @Param("ownerId") ownerId: String
    ): Long
}