package com.beancounter.auth

import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * Supports simple annotation to disable Auth in your tests.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Import(
    NoAuthConfig::class
)
@TestPropertySource(
    properties = [
        "auth.enabled=false",
        "auth.audience=test-audience",
        "auth.email=some-email@somewhere",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=test-uri"
    ]
)
annotation class AutoConfigureNoAuth
