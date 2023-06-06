package com.beancounter.shell.commands

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.utils.BcJson
import com.beancounter.shell.config.EnvConfig
import com.fasterxml.jackson.core.JsonProcessingException
import org.jline.reader.LineReader
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

/**
 * Interactive access to BeanCounter from the commandline.
 */
@ShellComponent
class UserCommands(
    private val loginService: LoginService,
    private val registrationService: RegistrationService,
    private val envConfig: EnvConfig,

) {
    @Lazy
    @Autowired(required = false)
    lateinit var lineReader: LineReader

    private val bcJson = BcJson()

    @ShellMethod("Identify yourself")
    fun login(
        @ShellOption(help = "User ID") user: String,
    ) {
        val password = lineReader.readLine("Password: ", '*')
        loginService.login(user, password, envConfig.client)
    }

    @ShellMethod("What's my access token?")
    fun token(): String {
        return if (registrationService.token == null) {
            "Not logged in"
        } else {
            registrationService.token!!
        }
    }

    @ShellMethod("Who am I?")
    fun me(): String {
        return bcJson.writer.writeValueAsString(registrationService.me())
    }

    @ShellMethod("Register your Account")
    @Throws(JsonProcessingException::class)
    fun register(emailClaim: String): String {
        val token = registrationService.jwtToken
        return bcJson.writer
            .writeValueAsString(
                registrationService
                    .register(RegistrationRequest(token.token.getClaim(emailClaim))),
            )
    }
}
