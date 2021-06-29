package com.beancounter.shell

import com.beancounter.auth.client.AuthClientConfig
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.common.utils.UtilConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * Starts the Shell.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@SpringBootApplication(
    scanBasePackageClasses = [AuthClientConfig::class, ShareSightConfig::class, UtilConfig::class, ClientConfig::class],
    scanBasePackages = ["com.beancounter.shell"]
)
@EnableConfigurationProperties
open class ShellRunner

fun main(args: Array<String>) {
    runApplication<ShellRunner>(*args)
}
