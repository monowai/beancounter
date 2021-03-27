package com.beancounter.auth.client

import com.beancounter.auth.common.TokenService
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(LoginService::class, AuthBeans::class, TokenService::class)
@ImportAutoConfiguration(
    HttpMessageConvertersAutoConfiguration::class, FeignAutoConfiguration::class
)
class AuthClientConfig
