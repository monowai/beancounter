package com.beancounter.marketdata

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.sharesight.ShareSightConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

/**
 * Data persistence service.
 */
@SpringBootApplication(
    scanBasePackageClasses = [FxTransactions::class, ShareSightConfig::class],
    scanBasePackages = [
        "com.beancounter.auth",
        "com.beancounter.common.utils",
        "com.beancounter.common.telemetry",
        "com.beancounter.common.exception",
        "com.beancounter.marketdata"
    ]
)
@EntityScan("com.beancounter.common.model", "com.beancounter.marketdata.assets")
@EnableWebSecurity
@EnableConfigurationProperties
class MarketDataBoot

fun main(args: Array<String>) {
    runApplication<MarketDataBoot>(args = args)
}