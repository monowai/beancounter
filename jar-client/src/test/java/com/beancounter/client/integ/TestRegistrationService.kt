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

@AutoConfigureStubRunner(stubsMode = StubRunnerProperties.StubsMode.LOCAL, ids = ["org.beancounter:svc-data:+:stubs:10999"])
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
class TestRegistrationService {
    @Autowired
    private val registrationService: RegistrationService? = null

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun is_RegisteringAuthenticatedUser() {
        setupAuth("token")
        assertThat(registrationService!!.jwtToken).isNotNull
        assertThat(registrationService.token).isNotNull
        // Currently matching is on email
        val registeredUser = registrationService
            .register(RegistrationRequest("blah@blah.com"))
        assertThat(registeredUser).hasNoNullFieldsOrProperties()
        org.junit.jupiter.api.Assertions.assertThrows(UnauthorizedException::class.java) { registrationService.me() }
    }

    @Test
    fun is_UnauthenticatedUserRejectedFromRegistration() {
        // Setup the authenticated context
        setupAuth("not@authenticated.com")
        assertThrows(UnauthorizedException::class.java) {
            registrationService!!.register(RegistrationRequest("invalid user"))
        }
    }

    private fun setupAuth(email: String) {
        val jwt = TokenUtils().getUserToken(SystemUser(email, email))
        Mockito.`when`(jwtDecoder.decode(email)).thenReturn(jwt)
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwtDecoder.decode(email))
    }
}
