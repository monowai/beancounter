package com.beancounter.auth.client

import com.beancounter.auth.AuthConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.openfeign.EnableFeignClients
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
@EnableFeignClients(basePackages = ["com.beancounter.auth.client"])
@Import(
    AuthConfig::class,
    JwtTokenCacheService::class,
    LoginService::class
)
@Configuration
class ClientPasswordConfig