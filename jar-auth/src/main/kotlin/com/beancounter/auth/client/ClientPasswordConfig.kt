package com.beancounter.auth.client

import com.beancounter.auth.AuthConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Login with username and password.
 */
@ConditionalOnProperty(
    value = ["auth.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@Import(
    AuthConfig::class,
    LoginService::class
)
@Configuration
class ClientPasswordConfig