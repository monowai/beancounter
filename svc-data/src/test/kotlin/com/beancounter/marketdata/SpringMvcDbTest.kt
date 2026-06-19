package com.beancounter.marketdata

import org.junit.jupiter.api.Tag
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
import org.springframework.test.context.ActiveProfiles
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * A custom annotation that combines common configurations for Spring Boot integration tests.
 *
 * Assumes the h2db active profile and wires up mock mvc.
 *
 * Note: the active profile is hard-wired via the meta-annotation [ActiveProfiles] below.
 * A custom `profiles` attribute aliased with `@AliasFor` does not survive Kotlin annotation
 * compilation reliably, so it was dropped — no caller passed a non-default value.
 */
@Target(CLASS)
@Retention(RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = AFTER_CLASS)
@Tag("db")
// Slow!
@ActiveProfiles("h2db")
annotation class SpringMvcDbTest
