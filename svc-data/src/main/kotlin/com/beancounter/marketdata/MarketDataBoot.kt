package com.beancounter.marketdata

import com.beancounter.auth.server.WebAuthFilterConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.sharesight.ShareSightConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

/**
 * Data persistence service.
 */
@SpringBootApplication(
    scanBasePackageClasses = [WebAuthFilterConfig::class, FxTransactions::class, ShareSightConfig::class],
    scanBasePackages = [
        "com.beancounter.marketdata",
        "com.beancounter.auth",
        "com.beancounter.common.utils",
        "com.beancounter.common.exception",
    ],
)
@EntityScan("com.beancounter.common.model")
@EnableWebSecurity
class MarketDataBoot

fun main(args: Array<String>) {
    runApplication<MarketDataBoot>(args = args)
}
