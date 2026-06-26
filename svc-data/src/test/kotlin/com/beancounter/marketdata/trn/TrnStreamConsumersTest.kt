package com.beancounter.marketdata.trn

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.input.TrustedTrnImportRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * DATA-4Z: the RabbitMQ CSV-import consumer runs on a thread with no SecurityContext, so any
 * downstream getOrThrow()/isServiceToken() threw UnauthorizedException("Not authorised") and the
 * message dead-lettered after exhausting the AMQP retry policy. The consumer must establish an
 * M2M service auth context before processing and clear it afterwards.
 */
class TrnStreamConsumersTest {
    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun serviceToken(): JwtAuthenticationToken {
        val jwt =
            Jwt
                .withTokenValue("m2m")
                .header("alg", "none")
                .claim("scope", AuthConstants.SYSTEM)
                .build()
        return JwtAuthenticationToken(jwt)
    }

    @Test
    fun `csv consumer processes inside an M2M service context and clears it afterwards`() {
        val authConfig = mock<AuthConfig>()
        whenever(authConfig.clientSecret).thenReturn("secret")
        val loginService = mock<LoginService>()
        whenever(loginService.authConfig).thenReturn(authConfig)
        whenever(loginService.loginM2m(any())).thenReturn(mock<OpenIdResponse>())
        whenever(loginService.setAuthContext(any())).thenAnswer {
            SecurityContextHolder.getContext().authentication = serviceToken()
            it.arguments[0]
        }

        val tokenService = TokenService(mock<AuthConfig>())
        var serviceTokenDuringProcessing: Boolean? = null
        val importService = mock<TrnImportService>()
        whenever(importService.fromCsvImport(any())).thenAnswer {
            serviceTokenDuringProcessing = tokenService.isServiceToken
            emptySet<Any>()
        }

        val consumers = TrnStreamConsumers(importService, providerFor(loginService))
        consumers.csvImportConsumer().accept(mock<TrustedTrnImportRequest>())

        // getOrThrow() -> isServiceAccount() -> isServiceToken would have resolved during processing.
        assertThat(serviceTokenDuringProcessing).isTrue()
        // Context must not leak onto the pooled AMQP thread.
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `csv consumer still processes when no LoginService is configured`() {
        // auth.enabled=false contexts (e.g. the contracts test profile) have no LoginService
        // bean. The consumer must still process the message rather than failing to construct.
        var processed = false
        val importService = mock<TrnImportService>()
        whenever(importService.fromCsvImport(any())).thenAnswer {
            processed = true
            emptySet<Any>()
        }

        val consumers = TrnStreamConsumers(importService, providerFor(null))
        consumers.csvImportConsumer().accept(mock<TrustedTrnImportRequest>())

        assertThat(processed).isTrue()
    }

    private fun providerFor(loginService: LoginService?): ObjectProvider<LoginService> {
        val provider = mock<ObjectProvider<LoginService>>()
        whenever(provider.ifAvailable).thenReturn(loginService)
        return provider
    }
}