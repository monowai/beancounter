package com.beancounter.agent

import io.micrometer.context.ContextRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Hooks

/**
 * Bridges Spring Security's thread-local [SecurityContextHolder] into
 * Project Reactor's context so values populated on the inbound request
 * thread survive scheduler hops inside [ChatClient.stream()].
 *
 * Without this, Spring AI's stream-mode tool callbacks run on Reactor
 * IO/parallel threads with an empty [SecurityContextHolder] — and shared
 * [TokenService.jwt] (`jar-auth`) throws `UnauthorizedException("Not
 * authorised")` because there's no authentication object to read. The
 * non-streaming `/agent/query` path didn't hit this because `.call()`
 * runs synchronously on the request thread.
 *
 * [Hooks.enableAutomaticContextPropagation] makes Reactor capture and
 * restore registered ThreadLocals at every operator boundary. We register
 * `SecurityContextHolder` against `ContextRegistry` so Reactor knows what
 * to copy. Spring Security 6.5+ does not auto-register a
 * `ThreadLocalAccessor` for the servlet `SecurityContextHolder`, so this
 * config wires it up explicitly.
 */
@Configuration
class SecurityContextPropagationConfig {
    private val log = LoggerFactory.getLogger(SecurityContextPropagationConfig::class.java)

    @PostConstruct
    fun enablePropagation() {
        ContextRegistry
            .getInstance()
            .registerThreadLocalAccessor(
                SECURITY_CONTEXT_KEY,
                { SecurityContextHolder.getContext() },
                { ctx -> SecurityContextHolder.setContext(ctx as SecurityContext) },
                { SecurityContextHolder.clearContext() }
            )
        Hooks.enableAutomaticContextPropagation()
        log.info(
            "Reactor automatic context propagation enabled — SecurityContext " +
                "will travel into ChatClient.stream() tool callbacks."
        )
    }

    companion object {
        const val SECURITY_CONTEXT_KEY = "spring.security.context"
    }
}