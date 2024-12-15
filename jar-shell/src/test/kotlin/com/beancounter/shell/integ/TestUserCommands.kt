package com.beancounter.shell.integ

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.TokenUtils
import com.beancounter.auth.model.LoginRequest
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.RegistrationService
import com.beancounter.client.services.RegistrationService.RegistrationGateway
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.shell.commands.UserCommands
import com.beancounter.shell.config.ShellConfig
import org.assertj.core.api.Assertions.assertThat
import org.jline.reader.LineReader
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

private const val EMAIL = "blah@blah.com"

/**
 * User commands related to AUTH activities.
 */
@SpringBootTest(classes = [ShellConfig::class, MockAuthConfig::class])
class TestUserCommands {
    @Autowired
    private lateinit var authConfig: AuthConfig

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var tokenService: TokenService

    @MockitoBean
    private lateinit var lineReader: LineReader
    private var registrationGateway: RegistrationGateway =
        Mockito.mock(RegistrationGateway::class.java)

    @Autowired
    private lateinit var tokenUtils: TokenUtils

    private val jwtDecoder: JwtDecoder = Mockito.mock(JwtDecoder::class.java)

    @Test
    fun is_UnauthorizedThrowing() {
        val userCommands = getUserCommands()
        assertThrows(UnauthorizedException::class.java) { userCommands.me() }
        assertThrows(UnauthorizedException::class.java) { userCommands.register() }
    }

    private fun getUserCommands(): UserCommands {
        val registrationService =
            RegistrationService(
                registrationGateway,
                jwtDecoder,
                tokenService
            )
        val userCommands = UserCommands(registrationService)
        userCommands.lineReader = lineReader
        return userCommands
    }

    @Test
    fun is_LoginReturningMe() {
        val userId = "simple"
        val password = "password"
        Mockito
            .`when`(
                lineReader.readLine(
                    "Password: ",
                    '*'
                )
            ).thenReturn(password)

        val systemUser =
            SystemUser(
                userId,
                EMAIL
            )
        mockAuthConfig.login(EMAIL)
        val authResponse =
            OpenIdResponse(
                userId,
                scope = "beancounter",
                expiry = 0L,
                refreshToken = "",
                type = "password"
            )

        Mockito
            .`when`(
                registrationGateway.auth(
                    LoginRequest(
                        userId,
                        password
                    )
                )
            ).thenReturn(authResponse)

        val userCommands = getUserCommands()
        val jwt = tokenUtils.getSystemUserToken(systemUser)
        Mockito
            .`when`(jwtDecoder.decode(authResponse.token))
            .thenReturn(jwt)

        // Can I login?
        userCommands.login(userId)
        Mockito
            .`when`(
                registrationGateway.register(
                    tokenService.bearerToken,
                    RegistrationRequest()
                )
            ).thenReturn(RegistrationResponse(systemUser))
        userCommands.register()

        // Is my token in the SecurityContext and am I Me?
        Mockito
            .`when`(registrationGateway.me(tokenService.bearerToken))
            .thenReturn(RegistrationResponse(systemUser))
        val me =
            objectMapper.readValue(
                userCommands.me(),
                SystemUser::class.java
            )
        assertThat(me).isNotNull.hasFieldOrPropertyWithValue(
            "id",
            systemUser.id
        )
        assertThat(userCommands.token()).isEqualTo(tokenService.bearerToken)
        Mockito
            .`when`(
                registrationGateway.register(
                    tokenService.bearerToken,
                    RegistrationRequest()
                )
            ).thenReturn(RegistrationResponse(systemUser))

        val registrationResponse = userCommands.register()
        val registered =
            objectMapper
                .readValue(
                    registrationResponse,
                    SystemUser::class.java
                )
        assertThat(registered)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "id",
                systemUser.id
            )
    }
}