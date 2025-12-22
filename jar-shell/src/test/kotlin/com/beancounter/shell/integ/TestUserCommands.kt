package com.beancounter.shell.integ

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.TokenUtils
import com.beancounter.auth.model.LoginRequest
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.shell.commands.UserCommands
import com.beancounter.shell.config.ShellConfig
import org.assertj.core.api.Assertions.assertThat
import org.jline.reader.LineReader
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
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
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var tokenService: TokenService

    @MockitoBean
    private lateinit var lineReader: LineReader

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var registrationService: RegistrationService

    @Autowired
    private lateinit var tokenUtils: TokenUtils

    @Test
    fun is_UnauthorizedThrowing() {
        `when`(registrationService.me()).thenThrow(UnauthorizedException("Not logged in"))
        `when`(registrationService.register(any<RegistrationRequest>()))
            .thenThrow(UnauthorizedException("Not logged in"))

        val userCommands = UserCommands(registrationService)
        userCommands.lineReader = lineReader
        assertThrows(UnauthorizedException::class.java) { userCommands.me() }
        assertThrows(UnauthorizedException::class.java) { userCommands.register() }
    }

    @Test
    fun is_LoginReturningMe() {
        val userId = "simple"
        val password = "password"
        `when`(
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

        `when`(
            registrationService.login(
                LoginRequest(
                    userId,
                    password
                )
            )
        ).thenReturn(authResponse)

        val userCommands = UserCommands(registrationService)
        userCommands.lineReader = lineReader

        val jwt = tokenUtils.getSystemUserToken(systemUser)
        `when`(jwtDecoder.decode(authResponse.token))
            .thenReturn(jwt)

        // Can I login?
        userCommands.login(userId)
        `when`(registrationService.register(any<RegistrationRequest>()))
            .thenReturn(systemUser)
        userCommands.register()

        // Is my token in the SecurityContext and am I Me?
        `when`(registrationService.me())
            .thenReturn(systemUser)
        val me =
            objectMapper.readValue(
                userCommands.me(),
                SystemUser::class.java
            )
        assertThat(me).isNotNull.hasFieldOrPropertyWithValue(
            "id",
            systemUser.id
        )
        // Mock the token property on the mocked service
        `when`(registrationService.token)
            .thenReturn(tokenService.bearerToken)
        assertThat(userCommands.token()).isEqualTo(tokenService.bearerToken)
        `when`(registrationService.register(any<RegistrationRequest>()))
            .thenReturn(systemUser)

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