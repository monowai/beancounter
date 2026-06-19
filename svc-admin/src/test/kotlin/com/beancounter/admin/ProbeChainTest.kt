package com.beancounter.admin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpStatus

/**
 * K8s liveness, readiness and startup probes hit the actuator health
 * endpoint (and its sub-paths liveness, readiness) unauthenticated.
 * Verifies the probe filter chain runs before the Basic-auth chain —
 * otherwise the kubelet receives a 401 and fails the startup probe.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.security.oauth2.client.registration.auth0.client-id=test-client",
        "spring.security.oauth2.client.registration.auth0.client-secret=test-secret",
        "spring.security.oauth2.client.registration.auth0.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.auth0.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.auth0.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.provider.auth0.authorization-uri=https://example.test/authorize",
        "spring.security.oauth2.client.provider.auth0.token-uri=https://example.test/oauth/token",
        "spring.security.oauth2.client.provider.auth0.jwk-set-uri=https://example.test/.well-known/jwks.json",
        "spring.security.oauth2.client.provider.auth0.user-info-uri=https://example.test/userinfo",
        "spring.security.oauth2.client.provider.auth0.user-name-attribute=sub",
        "beancounter.admin.client.bearer-token=",
        "management.server.port=0",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoints.web.exposure.include=health,info,metrics"
    ]
)
class ProbeChainTest {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    // Actuator endpoints live on a separate management port in production
    // (application.yml sets management.server.port=9531). Spring Boot binds
    // a random port at test time and exposes it as local.management.port.
    @Value("\${local.management.port}")
    private var managementPort: Int = 0

    @Test
    fun `actuator health permits anonymous access`() {
        val response =
            restTemplate.getForEntity(
                "http://localhost:$managementPort/actuator/health",
                String::class.java
            )
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `actuator health liveness permits anonymous access`() {
        val response =
            restTemplate.getForEntity(
                "http://localhost:$managementPort/actuator/health/liveness",
                String::class.java
            )
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `actuator health readiness permits anonymous access`() {
        val response =
            restTemplate.getForEntity(
                "http://localhost:$managementPort/actuator/health/readiness",
                String::class.java
            )
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }
}