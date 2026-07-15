package com.beancounter.marketdata.classification

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Selects the active [ClassificationEnricher] from `beancounter.market.providers.classification`.
 *
 * Defaults to `alpha`, so production behaviour is unchanged until an operator opts into `eodhd`
 * (which additionally needs a fundamentals-capable EODHD key on
 * `beancounter.market.providers.eodhd.key`). Consumers inject [ClassificationEnricher] and receive
 * this primary bean; the concrete provider services remain injectable by type.
 */
@Configuration
class ClassificationEnricherConfig {
    @Bean
    @Primary
    fun classificationEnricher(
        alpha: AlphaClassificationEnricher,
        eodhd: EodhdClassificationEnricher,
        @Value($$"${beancounter.market.providers.classification:alpha}") provider: String
    ): ClassificationEnricher =
        when (provider.trim().lowercase()) {
            "eodhd" -> eodhd
            else -> alpha
        }
}