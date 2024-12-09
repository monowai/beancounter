package com.beancounter.shell.commands

import com.beancounter.auth.model.LoginRequest
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.BcJson.Companion.writer
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
    private val registrationService: RegistrationService
) {
    @Lazy
    @Autowired(required = false)
    lateinit var lineReader: LineReader

    private val bcJson = BcJson()

    @ShellMethod("Identify yourself")
    fun login(
        @ShellOption(
            help = "User ID",
            defaultValue = ""
        ) user: String?
    ) {
        val u =
            if (user.isNullOrBlank()) {
                lineReader.readLine("User: ")
            } else {
                user
            }
        val password =
            lineReader.readLine(
                "Password: ",
                '*'
            )
        registrationService.login(
            LoginRequest(
                u,
                password
            )
        )
    }

    @ShellMethod("What's my access token?")
    fun token(): String = registrationService.token

    @ShellMethod("Who am I?")
    fun me(): String = writer.writeValueAsString(registrationService.me())

    @ShellMethod("Register your Account")
    fun register(): String =
        writer.writeValueAsString(
            registrationService
                .register(RegistrationRequest())
        )
}