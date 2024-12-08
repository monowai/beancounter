package com.beancounter.common.contracts

/**
 * Request to register a user and record the additional properties.
 * Email is unique in the system, and we extract that from a JWT
 */
data class RegistrationRequest(
    val active: Boolean = true,
)
