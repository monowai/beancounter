package com.beancounter.marketdata.broker

import com.beancounter.common.contracts.BrokerWithAccounts
import com.beancounter.common.contracts.SettlementAccountMapping
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.trn.TrnRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class BrokerService(
    private val brokerRepository: BrokerRepository,
    private val trnRepository: TrnRepository,
    private val settlementAccountRepository: BrokerSettlementAccountRepository,
    private val assetRepository: AssetRepository
) {
    fun findByOwner(owner: SystemUser): List<Broker> = brokerRepository.findByOwner(owner)

    fun findByOwnerWithAccounts(owner: SystemUser): List<BrokerWithAccounts> {
        val brokers = brokerRepository.findByOwner(owner)
        return brokers.map { broker ->
            val settlementAccounts = getSettlementAccountMappings(broker.id)
            BrokerWithAccounts(broker, settlementAccounts)
        }
    }

    fun findById(
        id: String,
        owner: SystemUser
    ): Optional<Broker> = brokerRepository.findByIdAndOwner(id, owner)

    fun findByIdWithAccounts(
        id: String,
        owner: SystemUser
    ): Optional<BrokerWithAccounts> {
        val broker = brokerRepository.findByIdAndOwner(id, owner)
        return broker.map { b ->
            val settlementAccounts = getSettlementAccountMappings(b.id)
            BrokerWithAccounts(b, settlementAccounts)
        }
    }

    private fun getSettlementAccountMappings(brokerId: String): List<SettlementAccountMapping> =
        settlementAccountRepository.findByBrokerId(brokerId).map { sa ->
            SettlementAccountMapping(
                currencyCode = sa.currencyCode,
                accountId = sa.account.id,
                accountName = sa.account.name
            )
        }

    @Transactional
    fun create(
        broker: Broker,
        settlementAccounts: Map<String, String>? = null
    ): Broker {
        // Check if broker with same name already exists for this owner
        val existing = brokerRepository.findByNameAndOwner(broker.name, broker.owner)
        if (existing.isPresent) {
            throw BusinessException("Broker with name '${broker.name}' already exists")
        }
        val savedBroker = brokerRepository.save(broker)
        updateSettlementAccounts(savedBroker, settlementAccounts)
        return savedBroker
    }

    private fun updateSettlementAccounts(
        broker: Broker,
        settlementAccounts: Map<String, String>?
    ) {
        // Delete existing settlement accounts
        settlementAccountRepository.deleteByBrokerId(broker.id)

        // Add new settlement accounts if provided
        settlementAccounts?.forEach { (currencyCode, accountId) ->
            val account =
                assetRepository
                    .findById(accountId)
                    .orElseThrow { BusinessException("Settlement account not found: $accountId") }
            val settlementAccount =
                BrokerSettlementAccount(
                    broker = broker,
                    currencyCode = currencyCode,
                    account = account
                )
            settlementAccountRepository.save(settlementAccount)
        }
    }

    @Transactional
    fun update(
        id: String,
        owner: SystemUser,
        name: String?,
        accountNumber: String?,
        notes: String?,
        settlementAccounts: Map<String, String>? = null
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
        val savedBroker = brokerRepository.save(updated)

        // Update settlement accounts if provided
        if (settlementAccounts != null) {
            updateSettlementAccounts(savedBroker, settlementAccounts)
        }

        return savedBroker
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

        // Delete settlement accounts first
        settlementAccountRepository.deleteByBrokerId(id)
        brokerRepository.delete(broker)
    }
}