package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration

/**
 * Verifies that auth can be disabled correctly.
 */
@SpringBootTest(properties = ["auth.enabled=false"])
@ContextConfiguration(classes = [TokenService::class, LoginService::class, AuthConfig::class])
class AuthDisabledTest {
    @Autowired
    lateinit var springContext: ApplicationContext

    @MockBean
    lateinit var tokenService: TokenService

    @Test
    fun isDisabled() {
        assertThat(springContext).isNotNull
        assertThat(springContext.environment.getProperty("auth.enabled")).isEqualTo("false")

        assertThrows(BeansException::class.java) {
            springContext.getBean(LoginService::class.java)
        }
        assertThat(springContext.getBean(TokenService::class.java)).isNotNull
    }
}