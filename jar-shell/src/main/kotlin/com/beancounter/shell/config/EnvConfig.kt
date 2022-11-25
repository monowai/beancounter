package com.beancounter.shell.config

import org.springframework.beans.factory.annotation.Value

/**
 * Client environment configuration.
 */
data class EnvConfig(
    @Value("\${auth.realm:beancounter}")
    val realm: String? = null,
    @Value("\${auth.client:not-set}")
    var client: String,
    @Value("\${auth.client:http://yourserver}")
    val uri: String? = null,
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:notset}")
    var apiPath: String,
    @Value("\${marketdata.url:http://localhost:9510/api}")
    var marketDataUrl: String
)
