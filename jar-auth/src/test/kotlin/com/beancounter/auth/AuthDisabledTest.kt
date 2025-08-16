package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test suite for authentication when disabled to ensure proper configuration.
 *
 * This class tests:
 * - Spring context initialization with authentication disabled
 * - Bean availability when authentication is disabled
 * - Configuration property validation
 * - Error handling for disabled authentication components
 *
 * Tests verify that when authentication is disabled, the appropriate
 * beans are available and unavailable as expected.
 */
@SpringBootTest(properties = ["auth.enabled=false"])
@ContextConfiguration(classes = [TokenService::class, LoginService::class, AuthConfig::class])
class AuthDisabledTest {
    @Autowired
    lateinit var springContext: ApplicationContext

    @MockitoBean
    lateinit var tokenService: TokenService

    @Test
    fun `should disable authentication and configure beans correctly`() {
        assertThat(springContext).isNotNull
        assertThat(springContext.environment.getProperty("auth.enabled")).isEqualTo("false")

        assertThrows(BeansException::class.java) {
            springContext.getBean(LoginService::class.java)
        }
        assertThat(springContext.getBean(TokenService::class.java)).isNotNull
    }
}