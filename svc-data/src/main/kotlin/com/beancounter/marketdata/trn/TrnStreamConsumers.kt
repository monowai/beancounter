package com.beancounter.marketdata.trn

import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Consumer

/**
 * Spring Cloud Stream functional consumers for transaction processing.
 * Controlled by stream.enabled property in application.yml
 */
@Configuration
class TrnStreamConsumers(
    private val trnImportService: TrnImportService
) {
    private val log = LoggerFactory.getLogger(TrnStreamConsumers::class.java)

    @Bean
    fun csvImportConsumer(): Consumer<TrustedTrnImportRequest> =
        Consumer { request ->
            log.trace("Processing CSV import request: {}", request)
            trnImportService.fromCsvImport(request)
        }

    @Bean
    fun trnEventConsumer(): Consumer<TrustedTrnEvent> =
        Consumer { event ->
            log.trace("Processing transaction event: {}", event)
            trnImportService.fromTrnRequest(event)
        }
}