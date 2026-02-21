package com.beancounter.position

import com.beancounter.client.config.ClientConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Boot all the things.
 */
@SpringBootApplication(
    scanBasePackageClasses = [ClientConfig::class],
    scanBasePackages = [
        "com.beancounter.position",
        "com.beancounter.auth",
        "com.beancounter.common.utils",
        "com.beancounter.common.telemetry",
        "com.beancounter.common.exception"
    ]
)
class PositionBoot

fun main(args: Array<String>) {
    runApplication<PositionBoot>(args = args)
}