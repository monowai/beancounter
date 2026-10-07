package com.beancounter.marketdata.classification

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Selects the active [ClassificationEnricher] from `beancounter.market.providers.classification`.
 *
 * The value is a CSV of provider keys - `alpha`, `eodhd`, `sec`. A single value selects that
 * enricher outright. Several (`sec,alpha`) wrap in a [ChainedClassificationEnricher] in the
 * order given: each asset goes to the first provider that can enrich it, so `sec,alpha` sends
 * US equities to the keyless SEC and everything else (ETFs, non-US listings) to AlphaVantage.
 *
 * Defaults to `alpha`, so production behaviour is unchanged until an operator opts in (for
 * `eodhd` that additionally needs a fundamentals-capable key on
 * `beancounter.market.providers.eodhd.key`). Consumers inject [ClassificationEnricher] and
 * receive this primary bean; the concrete provider services remain injectable by type.
 */
@Configuration
class ClassificationEnricherConfig {
    @Bean
    @Primary
    fun classificationEnricher(
        alpha: AlphaClassificationEnricher,
        eodhd: EodhdClassificationEnricher,
        sec: SecClassificationEnricher,
        @Value($$"${beancounter.market.providers.classification:alpha}") provider: String
    ): ClassificationEnricher {
        val selected =
            provider
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .map { key ->
                    when (key) {
                        "eodhd" -> eodhd
                        "sec" -> sec
                        else -> alpha
                    }
                }.distinct()
        return when (selected.size) {
            0 -> alpha
            1 -> selected.single()
            else -> ChainedClassificationEnricher(selected)
        }
    }
}