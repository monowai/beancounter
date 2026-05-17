package com.beancounter.marketdata.broker

import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import org.springframework.data.jpa.repository.JpaRepository
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
}