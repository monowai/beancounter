package com.beancounter.shell.integ

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.TokenUtils
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.client.LoginService.AuthGateway
import com.beancounter.auth.model.OAuth2Response
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
import org.assertj.core.api.Assertions.assertThat
import org.jline.reader.LineReader
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder

private const val EMAIL = "blah@blah.com"

/**
 * User commands related to AUTH activities.
 */
@SpringBootTest(classes = [ShellConfig::class, MockAuthConfig::class])
class TestUserCommands {
    private val bcJson = BcJson()

    @Autowired
    private lateinit var authConfig: AuthConfig

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private var client: String = "bc-dev"

    @Autowired
    private lateinit var tokenService: TokenService
    private var lineReader: LineReader = Mockito.mock(LineReader::class.java)
    private var authGateway: AuthGateway = Mockito.mock(AuthGateway::class.java)
    private var registrationGateway: RegistrationGateway = Mockito.mock(RegistrationGateway::class.java)
    private var jwtDecoder: JwtDecoder = Mockito.mock(JwtDecoder::class.java)

    private lateinit var userCommands: UserCommands

    @Autowired
    private lateinit var tokenUtils: TokenUtils

    @Autowired
    fun initAuth() {
        userCommands = UserCommands(
            LoginService(authGateway, jwtDecoder),
            RegistrationService(registrationGateway, tokenService),
            EnvConfig(client = client, apiPath = "/", marketDataUrl = "/"),
            lineReader,
        )
    }

    @Test
    fun is_UnauthorizedThrowing() {
        mockAuthConfig.resetAuth()
        assertThrows(UnauthorizedException::class.java) { userCommands.me() }
        assertThrows(UnauthorizedException::class.java) { userCommands.register(authConfig.claimEmail) }
    }

    @Test
    fun is_LoginReturningMe() {
        val userId = "simple"
        val password = "password"
        Mockito.`when`(lineReader.readLine("Password: ", '*'))
            .thenReturn(password)
        val loginRequest = LoginService.LoginRequest(client_id = client, username = userId, password = password)
        val systemUser = SystemUser(userId, EMAIL)
        mockAuthConfig.login(EMAIL)
        val jwt = tokenUtils.getUserToken(systemUser)
        val authResponse =
            OAuth2Response(userId, scope = "beancounter", expiry = 0L, refreshToken = "", type = "password")
        Mockito.`when`(authGateway.login(loginRequest))
            .thenReturn(authResponse)
        Mockito.`when`(jwtDecoder.decode(authResponse.token))
            .thenReturn(jwt)

        // Can I login?
        userCommands.login(userId)

        // Is my token in the SecurityContext and am I Me?
        Mockito.`when`(registrationGateway.me(tokenService.bearerToken))
            .thenReturn(RegistrationResponse(systemUser))
        val me = bcJson.objectMapper.readValue(userCommands.me(), SystemUser::class.java)
        assertThat(me).isNotNull.hasFieldOrPropertyWithValue("id", systemUser.id)
        assertThat(userCommands.token()).isEqualTo(authResponse.token)
        Mockito.`when`(
            registrationGateway.register(
                tokenService.bearerToken,
                RegistrationRequest(systemUser.email),
            ),
        )
            .thenReturn(RegistrationResponse(systemUser))
        val registrationResponse = userCommands.register(authConfig.claimEmail)
        val registered = bcJson.objectMapper
            .readValue(registrationResponse, SystemUser::class.java)
        assertThat(registered).isNotNull.hasFieldOrPropertyWithValue("id", userId)
    }
}
