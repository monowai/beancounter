package com.beancounter.shell.integ

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.client.LoginService.AuthGateway
import com.beancounter.auth.client.OAuth2Response
import com.beancounter.auth.common.TokenService
import com.beancounter.auth.common.TokenUtils
import com.beancounter.client.services.RegistrationService
import com.beancounter.client.services.RegistrationService.RegistrationGateway
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.shell.cli.UserCommands
import com.beancounter.shell.config.EnvConfig
import com.beancounter.shell.config.ShellConfig
import lombok.SneakyThrows
import org.jline.reader.LineReader
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder

@SpringBootTest(classes = [ShellConfig::class])
class TestUserCommands {
    private val bcJson = BcJson()

    @Value("\${auth.client}")
    private val client: String? = null
    private val tokenService = TokenService()
    private var lineReader: LineReader = Mockito.mock(LineReader::class.java)
    private var authGateway: AuthGateway = Mockito.mock(AuthGateway::class.java)
    private var registrationGateway: RegistrationGateway = Mockito.mock(RegistrationGateway::class.java)
    private var jwtDecoder: JwtDecoder = Mockito.mock(JwtDecoder::class.java)
    private var envConfig: EnvConfig = Mockito.mock(EnvConfig::class.java)

    private var userCommands: UserCommands = UserCommands(
        LoginService(authGateway, jwtDecoder),
        RegistrationService(registrationGateway, tokenService),
        envConfig,
        lineReader
    )

    @BeforeEach
    fun configEnvironment() {
        Mockito.`when`(envConfig.client).thenReturn(client)
    }

    @Test
    fun is_UnauthorizedThrowing() {
        SecurityContextHolder.getContext().authentication = null
        Mockito.`when`(registrationGateway.me(tokenService.bearerToken))
            .thenReturn(null)
        Assertions.assertThrows(UnauthorizedException::class.java) { userCommands.me() }
        Mockito.`when`(
            registrationGateway
                .register(tokenService.bearerToken, RegistrationRequest(null))
        )
            .thenReturn(null)
        Assertions.assertThrows(UnauthorizedException::class.java) { userCommands.register() }
    }

    @Test
    @SneakyThrows
    fun is_LoginReturningMe() {
        val userId = "simple"
        val password = "password"
        Mockito.`when`(lineReader.readLine("Password: ", '*'))
            .thenReturn(password)
        val authRequest = LoginService.AuthRequest(userId, password, client)
        val systemUser = SystemUser(userId, "blah@blah.com")
        val jwt = TokenUtils().getUserToken(systemUser)
        val authResponse = OAuth2Response(userId, 0L, null, "", "")
        Mockito.`when`(authGateway.login(authRequest))
            .thenReturn(authResponse)
        Mockito.`when`(jwtDecoder.decode(authResponse.token))
            .thenReturn(jwt)

        // Can I login?
        userCommands.login(userId)

        // Is my token in the SecurityContext and am I Me?
        Mockito.`when`(registrationGateway.me(tokenService.bearerToken))
            .thenReturn(RegistrationResponse(systemUser))
        val me = bcJson.objectMapper.readValue(userCommands.me(), SystemUser::class.java)
        org.assertj.core.api.Assertions.assertThat(me).isNotNull.hasFieldOrPropertyWithValue("id", systemUser.id)
        org.assertj.core.api.Assertions.assertThat(userCommands.token()).isEqualTo(authResponse.token)
        Mockito.`when`(
            registrationGateway.register(
                tokenService.bearerToken,
                RegistrationRequest(systemUser.email)
            )
        )
            .thenReturn(RegistrationResponse(systemUser))
        val registrationResponse = userCommands.register()
        val registered = bcJson.objectMapper
            .readValue(registrationResponse, SystemUser::class.java)
        org.assertj.core.api.Assertions.assertThat(registered).isNotNull.hasFieldOrPropertyWithValue("id", userId)
    }
}
