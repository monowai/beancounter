package com.beancounter.shell.config

import org.springframework.beans.factory.annotation.Value

/**
 * Client environment configuration.
 */
data class EnvConfig(
    @param:Value($$"${auth.realm:beancounter}")
    val realm: String? = null,
    @param:Value($$"${auth.client:not-set}")
    var client: String,
    @param:Value($$"${auth.client:https://yourserver}")
    val uri: String? = null,
    @param:Value($$"${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:notset}")
    var apiPath: String,
    @param:Value("\${marketdata.url:http://localhost:9510}")
    var marketDataUrl: String,
    @param:Value("\${marketdata.actuator:http://localhost:9510}")
    var mdActuator: String
)