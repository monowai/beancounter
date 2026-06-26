package com.beancounter.marketdata.trn

import com.beancounter.auth.client.LoginService
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import java.util.function.Consumer

/**
 * Spring Cloud Stream functional consumers for transaction processing.
 * Controlled by stream.enabled property in application.yml
 */
@Configuration
class TrnStreamConsumers(
    private val trnImportService: TrnImportService,
    // ObjectProvider: LoginService is gated on auth.enabled=true, so it is absent from
    // no-auth test contexts (contracts profile). Resolve it lazily and only establish the
    // M2M context when present — auth-disabled contexts need no service token.
    private val loginService: ObjectProvider<LoginService>
) {
    private val log = LoggerFactory.getLogger(TrnStreamConsumers::class.java)

    @Bean
    fun csvImportConsumer(): Consumer<TrustedTrnImportRequest> =
        Consumer { request ->
            withServiceContext {
                log.trace("Processing CSV import request: {}", request)
                trnImportService.fromCsvImport(request)
            }
        }

    @Bean
    fun trnEventConsumer(): Consumer<TrustedTrnEvent> =
        Consumer { event ->
            withServiceContext {
                log.trace("Processing transaction event: {}", event)
                trnImportService.fromTrnRequest(event)
            }
        }

    /**
     * DATA-4Z: AMQP consumer threads carry no SecurityContext, so any downstream
     * getOrThrow()/isServiceToken() threw UnauthorizedException("Not authorised") and the
     * message dead-lettered after exhausting the retry policy. Establish the cached M2M
     * service token for the duration of processing, then clear it so nothing leaks onto the
     * pooled thread.
     */
    private fun <T> withServiceContext(block: () -> T): T {
        val login = loginService.ifAvailable ?: return block()
        return try {
            login.setAuthContext(login.loginM2m())
            block()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}