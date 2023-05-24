package com.beancounter.auth.server

import com.beancounter.common.contracts.RegistrationResponse
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Register an authenticated token with the service so that it can
 * create data.
 */
interface Registration {
    fun register(jwt: Jwt): RegistrationResponse
}
