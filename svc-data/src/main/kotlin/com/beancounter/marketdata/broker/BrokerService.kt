package com.beancounter.marketdata.broker

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.trn.TrnRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class BrokerService(
    private val brokerRepository: BrokerRepository,
    private val trnRepository: TrnRepository
) {
    fun findByOwner(owner: SystemUser): List<Broker> = brokerRepository.findByOwner(owner)

    fun findById(
        id: String,
        owner: SystemUser
    ): Optional<Broker> = brokerRepository.findByIdAndOwner(id, owner)

    @Transactional
    fun create(broker: Broker): Broker {
        // Check if broker with same name already exists for this owner
        val existing = brokerRepository.findByNameAndOwner(broker.name, broker.owner)
        if (existing.isPresent) {
            throw BusinessException("Broker with name '${broker.name}' already exists")
        }
        return brokerRepository.save(broker)
    }

    @Transactional
    fun update(
        id: String,
        owner: SystemUser,
        name: String?,
        accountNumber: String?,
        notes: String?
    ): Broker {
        val broker =
            brokerRepository
                .findByIdAndOwner(id, owner)
                .orElseThrow { BusinessException("Broker not found: $id") }

        // Check if new name conflicts with existing broker
        if (name != null && name != broker.name) {
            val existing = brokerRepository.findByNameAndOwner(name, owner)
            if (existing.isPresent && existing.get().id != id) {
                throw BusinessException("Broker with name '$name' already exists")
            }
        }

        val updated =
            broker.copy(
                name = name ?: broker.name,
                accountNumber = accountNumber ?: broker.accountNumber,
                notes = notes ?: broker.notes
            )
        return brokerRepository.save(updated)
    }

    /**
     * Count transactions for a broker.
     * Used to check if a broker can be deleted.
     */
    fun countTransactions(
        id: String,
        owner: SystemUser
    ): Long {
        brokerRepository
            .findByIdAndOwner(id, owner)
            .orElseThrow { BusinessException("Broker not found: $id") }
        return trnRepository.countByBrokerId(id, owner)
    }

    @Transactional
    fun delete(
        id: String,
        owner: SystemUser
    ) {
        val broker =
            brokerRepository
                .findByIdAndOwner(id, owner)
                .orElseThrow { BusinessException("Broker not found: $id") }

        // Check for existing transactions
        val transactionCount = trnRepository.countByBrokerId(id, owner)
        if (transactionCount > 0) {
            throw BusinessException(
                "Cannot delete broker '${broker.name}' - it has $transactionCount transaction(s). " +
                    "Transfer transactions to another broker first."
            )
        }

        brokerRepository.delete(broker)
    }
}