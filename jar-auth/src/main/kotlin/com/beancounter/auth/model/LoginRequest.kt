package com.beancounter.auth.model

/**
 * POJO to support PASSWORD flow.
 */
data class LoginRequest(val user: String, val password: String)
