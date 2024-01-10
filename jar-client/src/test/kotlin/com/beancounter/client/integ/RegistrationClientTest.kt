package com.beancounter.client.integ

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

/**
 * Test all user registration behaviour.
 */
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class, MockAuthConfig::class])
@AutoConfigureMockAuth
class RegistrationClientTest {
    @Autowired
    private lateinit var registrationService: RegistrationService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var tokenService: TokenService

    @MockBean
    private lateinit var registrationGateway: RegistrationService.RegistrationGateway

    @Test
    fun registeringAuthenticatedUser() {
        mockAuthConfig.login()
        Mockito.`when`(registrationGateway.register(tokenService.bearerToken, RegistrationRequest()))
            .thenReturn(RegistrationResponse(SystemUser()))

        val registeredUser =
            registrationService
                .register(RegistrationRequest())
        assertThat(registeredUser).hasNoNullFieldsOrProperties()

        Mockito.`when`(registrationGateway.me(tokenService.bearerToken))
            .thenReturn(RegistrationResponse(registeredUser))

        val me = registrationService.me()
        assertThat(me)
            .usingRecursiveComparison()
            .isEqualTo(registeredUser)
    }

    @Test
    fun unauthenticatedUserRejectedFromRegistration() {
        mockAuthConfig.resetAuth()
        assertThrows(UnauthorizedException::class.java) {
            registrationService.register(RegistrationRequest())
        }
    }
}
