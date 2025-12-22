package com.beancounter.client.integ

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test all user registration behaviour.
 */
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class, MockAuthConfig::class])
@AutoConfigureMockAuth
class RegistrationClientTest {
    @MockitoBean
    private lateinit var registrationService: RegistrationService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `authenticated user can register for a BC account`() {
        mockAuthConfig.login()
        val expectedUser = SystemUser()

        `when`(registrationService.register(RegistrationRequest()))
            .thenReturn(expectedUser)

        val registeredUser = registrationService.register(RegistrationRequest())
        assertThat(registeredUser).hasNoNullFieldsOrProperties()

        `when`(registrationService.me())
            .thenReturn(registeredUser)

        val me = registrationService.me()
        assertThat(me)
            .usingRecursiveComparison()
            .isEqualTo(registeredUser)
    }

    @Test
    fun `cannot register without being authenticated`() {
        mockAuthConfig.logout()
        `when`(registrationService.register(RegistrationRequest()))
            .thenThrow(UnauthorizedException("Not authenticated"))

        assertThrows(UnauthorizedException::class.java) {
            registrationService.register(RegistrationRequest())
        }
    }
}