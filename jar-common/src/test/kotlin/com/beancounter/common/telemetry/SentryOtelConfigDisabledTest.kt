package com.beancounter.common.telemetry

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource

/**
 * Test SentryConfig is not wired when disabled.
 */
@SpringBootTest(classes = [SentryOtelConfig::class])
@TestPropertySource(
    properties = [
        "sentry.enabled=false"
    ]
)
class SentryOtelConfigDisabledTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun testSentryConfigNotWired() {
        assertThrows(NoSuchBeanDefinitionException::class.java) {
            applicationContext.getBean(SentryOtelConfig::class.java)
        }
    }
}