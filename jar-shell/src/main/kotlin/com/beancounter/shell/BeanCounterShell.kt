package com.beancounter.shell

import com.beancounter.auth.OAuthConfig
import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.common.utils.UtilConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * Starts an interactive command line shell to interact with the services.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@SpringBootApplication(
    scanBasePackageClasses = [
        ClientPasswordConfig::class,
        ShareSightConfig::class,
        UtilConfig::class,
        ClientConfig::class,
        OAuthConfig::class
    ],
    scanBasePackages = ["com.beancounter.shell", "com.beancounter.auth.client"]
)
@EnableConfigurationProperties
class ShellRunner

fun main(args: Array<String>) {
    runApplication<ShellRunner>(args = args)
}