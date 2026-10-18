package com.beancounter.position.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.util.ClassUtils

/**
 * Boot 4 moved Flyway auto-configuration into spring-boot-flyway. With only flyway-core on
 * the classpath, spring.flyway.enabled=true silently does nothing and no migration runs.
 */
class FlywayWiringTest {
    @Test
    fun `should have Flyway auto-configuration on the runtime classpath`() {
        assertThat(
            ClassUtils.isPresent(
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                javaClass.classLoader
            )
        ).isTrue()
    }
}