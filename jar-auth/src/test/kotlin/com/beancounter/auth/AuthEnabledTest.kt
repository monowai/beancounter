package com.beancounter.auth

import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.auth.client.LoginService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

/**
 * Verifies correctly configured auth wires the correct beans.
 */
@SpringBootTest(
    properties = [
        "auth.enabled=true",
        "auth.audience=test-audience",
        "auth.email=some-email",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=test-uri",
    ],
)
@ContextConfiguration(
    classes = [MockAuthConfig::class, AuthConfig::class, ClientPasswordConfig::class],
)
@ActiveProfiles("auth")
class AuthEnabledTest {
    @Autowired
    lateinit var springContext: ApplicationContext

    @Autowired
    lateinit var authConfig: AuthConfig

    @Value("\${auth.audience}")
    lateinit var audience: String

    @Test
    fun isEnabled() {
        assertThat(springContext).isNotNull
        assertThat(springContext.environment.getProperty("auth.enabled")).isEqualTo("true")
        assertThat(springContext.getBean(AuthConfig::class.java)).isNotNull
        assertThat(springContext.getBean(LoginService::class.java)).isNotNull
        assertThat(springContext.getBean(TokenService::class.java)).isNotNull
    }

    @Test
    fun isConfigCorrect() {
        assertThat(authConfig)
            .hasFieldOrPropertyWithValue("audience", audience)
            .hasFieldOrPropertyWithValue("claimEmail", "some-email")
    }
}
