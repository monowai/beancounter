package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Consumer

/**
 * Spring Cloud Stream functional consumer for portfolio updates.
 * Controlled by stream.enabled property in application.yml
 */
@Configuration
class PortfolioStreamConsumer(
    private val portfolioService: PortfolioService
) {
    private val log = LoggerFactory.getLogger(PortfolioStreamConsumer::class.java)

    @Bean
    fun portfolioConsumer(): Consumer<Portfolio> =
        Consumer { portfolio ->
            log.trace("Received portfolio update: {}", portfolio.code)
            portfolioService.maintain(portfolio)
        }
}