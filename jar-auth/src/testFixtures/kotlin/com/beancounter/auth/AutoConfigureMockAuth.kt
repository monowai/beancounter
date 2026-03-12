package com.beancounter.auth

import com.beancounter.auth.client.ClientPasswordConfig
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * Configures mock OAuth behaviour for unit testing.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@TestPropertySource(
    properties = [
        "auth.enabled=true",
        "auth.audience=test-audience",
        "auth.email=some-email@somewhere",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=test-uri"
    ]
)
@Import(
    MockAuthConfig::class,
    AuthConfig::class,
    ClientPasswordConfig::class
)
annotation class AutoConfigureMockAuth