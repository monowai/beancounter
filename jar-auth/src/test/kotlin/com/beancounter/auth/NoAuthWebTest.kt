package com.beancounter.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.context.ContextConfiguration

/**
 * Verify that the noop authorizer is deployed when auth is disabled in a web server.
 */
@SpringBootTest(properties = ["auth.enabled=false"])
@EnableWebSecurity
@ContextConfiguration(classes = [NoWebAuth::class, AuthConfig::class])
class NoAuthWebTest {
    @Autowired
    lateinit var springContext: ApplicationContext

    @Test
    fun isNoWebAuthEnabled() {
        assertThat(springContext).isNotNull
        assertThat(springContext.getBean(NoWebAuth::class.java)).isNotNull
    }
}