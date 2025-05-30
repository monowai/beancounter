package com.beancounter.shell.commands

import com.beancounter.client.services.ActuatorService
import com.beancounter.shell.config.EnvConfig
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import java.nio.file.FileSystems

/**
 * Environmental related commands.
 */
@ShellComponent
class EnvCommands(
    private val envConfig: EnvConfig,
    private val actuatorService: ActuatorService
) {
    @ShellMethod("Current working directory")
    fun pwd(): String =
        FileSystems
            .getDefault()
            .getPath("")
            .toAbsolutePath()
            .toString()

    @ShellMethod
    fun ping(): String = actuatorService.ping()

    @ShellMethod("Shell configuration")
    fun env(): String =
        ObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(
                mapOf(
                    Pair(
                        "MARKETDATA_URL",
                        envConfig.marketDataUrl
                    ),
                    Pair(
                        "ACTUATOR_URL",
                        envConfig.mdActuator
                    )
                )
            )
}