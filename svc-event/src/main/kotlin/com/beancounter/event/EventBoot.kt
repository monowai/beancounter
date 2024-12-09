package com.beancounter.event

import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.client.config.ClientConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

/**
 * Corporate Action Service.
 */
@SpringBootApplication(
    scanBasePackageClasses = [ClientConfig::class, ClientPasswordConfig::class],
    scanBasePackages = [
        "com.beancounter.event",
        "com.beancounter.auth",
        "com.beancounter.common.utils",
        "com.beancounter.common.telemetry",
        "com.beancounter.common.exception"
    ]
)
@EntityScan("com.beancounter.common.event")
@EnableAsync
@EnableCaching
@EnableWebSecurity
class EventBoot

fun main(args: Array<String>) {
    runApplication<EventBoot>(args = args)
}