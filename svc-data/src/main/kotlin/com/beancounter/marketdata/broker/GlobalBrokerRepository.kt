package com.beancounter.marketdata.broker

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GlobalBrokerRepository : JpaRepository<GlobalBroker, String> {
    fun findAllByOrderByName(): List<GlobalBroker>
}