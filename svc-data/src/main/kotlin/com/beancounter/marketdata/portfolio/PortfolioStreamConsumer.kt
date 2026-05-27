package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
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
            // Only apply valuation fields from the producer; identity/config
            // (code/name/active/currency/base/owner/cashPortfolioId) comes
            // from the DB to avoid stale values overwriting recent user edits.
            val existing = portfolioService.findOrNull(portfolio.id) ?: return@Consumer
            val updatedPortfolio =
                existing.copy(
                    marketValue = portfolio.marketValue,
                    irr = portfolio.irr,
                    gainOnDay = portfolio.gainOnDay,
                    assetClassification = portfolio.assetClassification,
                    valuedAt = portfolio.valuedAt,
                    lastUpdated = Instant.now()
                )
            portfolioService.maintain(updatedPortfolio)
        }
}