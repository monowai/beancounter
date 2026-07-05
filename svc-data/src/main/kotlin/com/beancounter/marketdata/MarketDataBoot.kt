package com.beancounter.marketdata

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.sharesight.ShareSightConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableAsync
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
@EntityScan(
    "com.beancounter.common.model",
    "com.beancounter.marketdata.assets",
    "com.beancounter.marketdata.tax",
    "com.beancounter.marketdata.broker",
    "com.beancounter.marketdata.providers.eodhd.news"
)
@EnableRetry
@EnableWebSecurity
@EnableConfigurationProperties
@EnableAsync
class MarketDataBoot

fun main(args: Array<String>) {
    runApplication<MarketDataBoot>(args = args)
}