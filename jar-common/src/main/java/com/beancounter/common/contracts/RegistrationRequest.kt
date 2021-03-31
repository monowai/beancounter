package com.beancounter.common.contracts

/**
 * Request to register a user and record the additional propertites. Email is unique in the system.
 */
data class RegistrationRequest(var email: String)
