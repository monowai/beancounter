package com.beancounter.shell.commands

import com.beancounter.shell.config.EnvConfig
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import java.nio.file.FileSystems
import java.util.TreeMap

/**
 * Environmental related commands.
 */
@ShellComponent
class EnvCommands(private val envConfig: EnvConfig) {
    @ShellMethod("Current working directory")
    fun pwd(): String {
        return FileSystems.getDefault().getPath("")
            .toAbsolutePath().toString()
    }

    @ShellMethod("Secrets")
    fun api(): String {
        return envConfig.apiPath
    }

    @ShellMethod("Shell configuration")
    @Throws(JsonProcessingException::class)
    fun env(): String {
        val config: MutableMap<String, String?> = TreeMap()
        config["MARKETDATA_URL"] = envConfig.marketDataUrl
        return ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(config)
    }
}
