package com.beancounter.admin

import de.codecentric.boot.admin.server.config.EnableAdminServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot Admin server. Discovers and renders actuator data from BC services
 * that register themselves via `spring-boot-admin-starter-client`. Deployed on
 * kauri as `bc-admin`.
 *
 * Auth: see [SecurityConfig]. Outbound calls to each client's actuator carry a
 * bearer token attached by [BearerTokenHttpHeadersProvider] (the BC services'
 * actuator endpoints require `SCOPE_beancounter:admin` or `SCOPE_beancounter:system`).
 */
@SpringBootApplication
@EnableAdminServer
class AdminBoot

fun main(args: Array<String>) {
    runApplication<AdminBoot>(args = args)
}