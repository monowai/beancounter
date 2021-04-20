package com.beancounter.client.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.RegistrationRequest
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
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * Test all user registration behaviour.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
class TestRegistrationService {
    @Autowired
    private lateinit var registrationService: RegistrationService

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun registeringAuthenticatedUser() {
        setupAuth("blah@blah.com")
        assertThat(registrationService.jwtToken).isNotNull
        assertThat(registrationService.token).isNotNull
        // Currently matching is on email
        val registeredUser = registrationService
            .register(RegistrationRequest("blah@blah.com"))
        assertThat(registeredUser).hasNoNullFieldsOrProperties()
        val me = registrationService.me()
        assertThat(me)
            .usingRecursiveComparison()
            .isEqualTo(registeredUser)
    }

    @Test
    fun unauthenticatedUserRejectedFromRegistration() {
        // Setup the authenticated context
        val email = "not@authenticated.com"
        setupAuth(email)
        assertThrows(UnauthorizedException::class.java) {
            registrationService.register(RegistrationRequest(email))
        }
    }

    private fun setupAuth(email: String) {
        val jwt = TokenUtils().getUserToken(SystemUser(email, email))
        Mockito.`when`(jwtDecoder.decode(email)).thenReturn(jwt)
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwtDecoder.decode(email))
    }
}
