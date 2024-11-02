package com.beancounter.position

import com.beancounter.auth.server.WebAuthFilterConfig
import com.beancounter.client.config.ClientConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

/**
 * Boot all the things.
 */
@SpringBootApplication(
    scanBasePackageClasses = [WebAuthFilterConfig::class, ClientConfig::class],
    exclude = [DataSourceAutoConfiguration::class],
    scanBasePackages = [
        "com.beancounter.position",
        "com.beancounter.auth",
        "com.beancounter.common.utils",
        "com.beancounter.common.telemetry",
        "com.beancounter.common.exception",
    ],
)
@EnableWebSecurity
class PositionBoot

fun main(args: Array<String>) {
    runApplication<PositionBoot>(args = args)
}
