package com.beancounter.auth

import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.context.TestPropertySource

/**
 * Supports simple annotation to disable Auth integration in your tests.
 * Assumes that WebAuth is still required, even though it is permitAll.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Import(
    NoWebAuth::class,
)
@TestPropertySource(
    properties = [
        "auth.enabled=false",
        "auth.web=false",
        "auth.audience=test-audience",
        "auth.email=some-email@somewhere",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost",
    ],
)
@EnableWebSecurity // Hmm, assumption is that all AUTH classes expect web-security. Is this correct?
annotation class AutoConfigureNoAuth
