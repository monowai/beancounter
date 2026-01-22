package com.beancounter.common.contracts

import com.beancounter.common.model.Broker

/**
 * Settlement account mapping for a broker (currency -> account asset ID).
 */
data class SettlementAccountMapping(
    val currencyCode: String,
    val accountId: String,
    val accountName: String? = null
)

/**
 * Broker with settlement accounts for API responses.
 */
data class BrokerWithAccounts(
    val id: String,
    val name: String,
    val accountNumber: String? = null,
    val notes: String? = null,
    val settlementAccounts: List<SettlementAccountMapping> = emptyList()
) {
    constructor(broker: Broker, settlementAccounts: List<SettlementAccountMapping> = emptyList()) : this(
        id = broker.id,
        name = broker.name,
        accountNumber = broker.accountNumber,
        notes = broker.notes,
        settlementAccounts = settlementAccounts
    )
}

/**
 * Single broker response contract.
 */
data class BrokerResponse(
    override var data: Broker
) : Payload<Broker>

/**
 * Single broker with accounts response contract.
 */
data class BrokerWithAccountsResponse(
    override var data: BrokerWithAccounts
) : Payload<BrokerWithAccounts>

/**
 * Multiple brokers response contract.
 */
data class BrokersResponse(
    override var data: Collection<Broker>
) : Payload<Collection<Broker>>

/**
 * Multiple brokers with accounts response contract.
 */
data class BrokersWithAccountsResponse(
    override var data: Collection<BrokerWithAccounts>
) : Payload<Collection<BrokerWithAccounts>>