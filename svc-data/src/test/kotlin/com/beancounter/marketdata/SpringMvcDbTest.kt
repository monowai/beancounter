package com.beancounter.marketdata

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
import org.springframework.test.context.ActiveProfiles
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * A custom annotation that combines common configurations for Spring Boot integration tests.
 *
 * Assumes the h2db active profile and wires up mock mvc
 */
@Target(CLASS)
@Retention(RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = AFTER_CLASS)
@Tag("db") // Slow!
@ActiveProfiles
annotation class SpringMvcDbTest(
    /**
     * Defines the active profiles to be used for the annotated test class.
     * Default is "h2db".
     */
    val profiles: Array<String> = ["h2db"],
)
