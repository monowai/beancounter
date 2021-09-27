package com.beancounter.position

import com.beancounter.auth.server.AuthServerConfig
import com.beancounter.client.config.ClientConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients

/**
 * Boot all the things.
 */
@SpringBootApplication(
    scanBasePackageClasses = [AuthServerConfig::class, ClientConfig::class],
    scanBasePackages = [
        "com.beancounter.position",
        "com.beancounter.client.services",
        "com.beancounter.common.exception"
    ]
)
@EnableFeignClients
class PositionBoot

fun main(args: Array<String>) {
    runApplication<PositionBoot>(args = args)
}
