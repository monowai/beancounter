package com.beancounter.common.input

/**
 * Input for creating or updating a broker.
 */
data class BrokerInput(
    val name: String,
    val accountNumber: String? = null,
    val notes: String? = null,
    // Map of currency code to settlement account asset ID
    val settlementAccounts: Map<String, String>? = null,
    // The global broker this is an account with; absent leaves it as it is
    val globalBrokerId: String? = null,
    // False deactivates the broker, true reactivates it; absent leaves it as it is
    val active: Boolean? = null
)