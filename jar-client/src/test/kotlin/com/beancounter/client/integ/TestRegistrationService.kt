package com.beancounter.client.integ

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties

/**
 * Test all user registration behaviour.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class, MockAuthConfig::class])
@AutoConfigureMockAuth
class TestRegistrationService {
    @Autowired
    private lateinit var registrationService: RegistrationService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var tokenService: TokenService

    @Test
    fun registeringAuthenticatedUser() {
        val email = "blah@blah.com"
        mockAuthConfig.setupAuth(email)
        val registeredUser = registrationService
            .register(RegistrationRequest(email))
        assertThat(registeredUser).hasNoNullFieldsOrProperties()
        val me = registrationService.me()
        assertThat(me)
            .usingRecursiveComparison()
            .isEqualTo(registeredUser)
    }

    @Test
    fun unauthenticatedUserRejectedFromRegistration() {
        // Set up the authenticated context
        val email = "not@authenticated.com"
        mockAuthConfig.setupAuth(email)
        assertThrows(UnauthorizedException::class.java) {
            registrationService.register(RegistrationRequest(email))
        }
    }
}
