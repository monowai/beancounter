package com.beancounter.event

import com.beancounter.auth.client.AuthClientConfig
import com.beancounter.auth.server.AuthServerConfig
import com.beancounter.client.config.ClientConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Corporate Action Service.
 */
@SpringBootApplication(
    scanBasePackageClasses = [AuthServerConfig::class, ClientConfig::class, AuthClientConfig::class],
    scanBasePackages = [
        "com.beancounter.key",
        "com.beancounter.event",
        "com.beancounter.common.utils",
        "com.beancounter.common.exception",
    ]
)
@EntityScan("com.beancounter.common.event")
@EnableAsync
@EnableCaching
class EventBoot

fun main(args: Array<String>) {
    runApplication<EventBoot>(args = args)
}
