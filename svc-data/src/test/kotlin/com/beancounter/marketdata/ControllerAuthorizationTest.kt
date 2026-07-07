package com.beancounter.marketdata

import com.beancounter.marketdata.registration.AuthController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val HANDLER_ANNOTATIONS =
    listOf(
        RequestMapping::class.java,
        GetMapping::class.java,
        PostMapping::class.java,
        PutMapping::class.java,
        PatchMapping::class.java,
        DeleteMapping::class.java
    )

/**
 * Architecture test: every `@RestController` in svc-data must be scope-gated,
 * either by a class-level `@PreAuthorize` or by a `@PreAuthorize` on every
 * HTTP handler method. Guards against a repeat of the un-guarded
 * BrokerController gap found in the 2026-07 auth audit.
 *
 * Allowlist is explicit and must not grow silently:
 * - [AuthController]: permitAll login proxy for bc-shell
 *   (`WebAuthFilterConfig`: "$apiPath/auth" is permitAll).
 */
class ControllerAuthorizationTest {
    private val basePackage = "com.beancounter.marketdata"

    private val allowlist = setOf(AuthController::class.java)

    @Test
    fun `every RestController is scope-gated by PreAuthorize`() {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))

        val unguarded =
            scanner
                .findCandidateComponents(basePackage)
                .map { Class.forName(it.beanClassName) }
                .filterNot { allowlist.contains(it) }
                .filterNot { isGuarded(it) }

        assertThat(unguarded)
            .withFailMessage(
                "Un-guarded @RestController(s) found (no class-level @PreAuthorize, and not " +
                    "every handler method has one): %s. Either add @PreAuthorize or, if the " +
                    "endpoint is intentionally open, add it to the explicit allowlist.",
                unguarded.map { it.name }
            ).isEmpty()
    }

    private fun isGuarded(controllerClass: Class<*>): Boolean {
        if (controllerClass.getAnnotation(PreAuthorize::class.java) != null) {
            return true
        }
        val handlerMethods =
            controllerClass.declaredMethods.filter { method ->
                HANDLER_ANNOTATIONS.any { method.isAnnotationPresent(it) }
            }
        if (handlerMethods.isEmpty()) {
            return false
        }
        return handlerMethods.all { it.isAnnotationPresent(PreAuthorize::class.java) }
    }
}