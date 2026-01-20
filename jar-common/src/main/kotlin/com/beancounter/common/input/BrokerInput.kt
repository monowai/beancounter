package com.beancounter.common.input

/**
 * Input for creating or updating a broker.
 */
data class BrokerInput(
    val name: String,
    val accountNumber: String? = null,
    val notes: String? = null
)