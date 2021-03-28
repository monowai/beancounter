package com.beancounter.shell.config

import org.springframework.beans.factory.annotation.Value

data class EnvConfig(
    @Value("\${auth.realm}")
    val realm: String? = null,
    @Value("\${auth.client}")
    var client: String,
    @Value("\${auth.client}")
    val uri: String? = null,
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    var apiPath: String,
    @Value("\${marketdata.url:http://localhost:9510/api}")
    var marketDataUrl: String,
)
