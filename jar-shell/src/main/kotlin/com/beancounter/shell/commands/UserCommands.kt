package com.beancounter.shell.commands

import com.beancounter.auth.model.LoginRequest
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.utils.BcJson
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
    private val registrationService: RegistrationService,

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
        registrationService.login(LoginRequest(user, password))
    }

    @ShellMethod("What's my access token?")
    fun token(): String {
        return registrationService.token
    }

    @ShellMethod("Who am I?")
    fun me(): String {
        return bcJson.writer.writeValueAsString(registrationService.me())
    }

    @ShellMethod("Register your Account")
    @Throws(JsonProcessingException::class)
    fun register(emailClaim: String): String {
        return bcJson.writer.writeValueAsString(
            registrationService
                .register(RegistrationRequest(emailClaim)),
        )
    }
}
