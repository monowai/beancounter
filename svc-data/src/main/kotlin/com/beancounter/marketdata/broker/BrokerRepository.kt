package com.beancounter.marketdata.broker

import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BrokerRepository : JpaRepository<Broker, String> {
    fun findByOwner(owner: SystemUser): List<Broker>

    fun findByIdAndOwner(
        id: String,
        owner: SystemUser
    ): Optional<Broker>

    fun findByNameAndOwner(
        name: String,
        owner: SystemUser
    ): Optional<Broker>
}