package com.beancounter.admin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * Sanity check: the SBA server context boots, all required beans resolve.
 * Catches misconfigured `@EnableAdminServer`, missing dep on the SBA server
 * starter, and bean conflicts in [SecurityConfig].
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.security.user.name=test",
        "spring.security.user.password=test",
        "beancounter.admin.client.bearer-token="
    ]
)
class AdminBootContextTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `context loads with SBA server + bearer-token provider beans`() {
        assertThat(context.getBean(BearerTokenHttpHeadersProvider::class.java)).isNotNull
        assertThat(context.getBean(SecurityConfig::class.java)).isNotNull
    }
}