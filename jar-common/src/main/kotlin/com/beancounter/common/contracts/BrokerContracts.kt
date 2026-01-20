package com.beancounter.common.contracts

import com.beancounter.common.model.Broker

/**
 * Single broker response contract.
 */
data class BrokerResponse(
    override var data: Broker
) : Payload<Broker>

/**
 * Multiple brokers response contract.
 */
data class BrokersResponse(
    override var data: Collection<Broker>
) : Payload<Collection<Broker>>