package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Consumer

/**
 * Spring Cloud Stream functional consumer for price data processing.
 * Controlled by stream.enabled property in application.yml
 */
@Configuration
class PriceStreamConsumer(
    private val priceService: PriceService
) {
    private val log = LoggerFactory.getLogger(PriceStreamConsumer::class.java)

    @Bean
    fun priceConsumer(): Consumer<PriceResponse> =
        Consumer { priceResponse ->
            log.trace("Received price data: {}", priceResponse)
            priceService.handle(priceResponse)
        }
}