package com.beancounter.marketdata.assets

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Pre-seed market index definitions loaded from `beancounter.indices.values`.
 *
 * Indices are reference assets (no SystemUser ownership) created at startup so
 * benchmark prices are queryable without manual asset registration.
 */
@ConfigurationProperties(prefix = "beancounter.indices")
@Component
class IndexConfig {
    var values: List<IndexDefinition> = emptyList()
}

data class IndexDefinition(
    /** Alpha Vantage symbol, e.g. "^GSPC". */
    var code: String = "",
    var name: String = "",
    /** ISO currency code the index is quoted in. */
    var currency: String = "USD"
)