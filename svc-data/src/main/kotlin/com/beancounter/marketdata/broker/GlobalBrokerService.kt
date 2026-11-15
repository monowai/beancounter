package com.beancounter.marketdata.broker

import com.beancounter.common.exception.BusinessException
import org.springframework.stereotype.Service

/** Reads the catalogue of brokerage firms. */
@Service
class GlobalBrokerService(
    private val globalBrokerRepository: GlobalBrokerRepository
) {
    fun findAll(): List<GlobalBroker> = globalBrokerRepository.findAllByOrderByName()

    /** Refuses an [id] that names no global broker; a broker with none passes. */
    fun verify(id: String?) {
        if (id != null && !globalBrokerRepository.existsById(id)) {
            throw BusinessException("Global broker not found: $id")
        }
    }
}