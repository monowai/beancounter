package com.beancounter.admin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * Sanity check: SBA server context boots, required beans resolve.
 * Catches misconfigured @EnableAdminServer, missing dep on the SBA server
 * starter, and bean conflicts in [SecurityConfig].
 *
 * Auth0 OIDC is configured with placeholder values so the OAuth2 client
 * registry initialises without reaching the network. Real values land via
 * AUTH0_CLIENT_ID / AUTH0_CLIENT_SECRET / AUTH0_DOMAIN env vars in bc-deploy.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.security.oauth2.client.registration.auth0.client-id=test-client",
        "spring.security.oauth2.client.registration.auth0.client-secret=test-secret",
        "spring.security.oauth2.client.registration.auth0.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.auth0.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.auth0.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        // Skip issuer-uri so Spring doesn't try to hit a /.well-known endpoint over the network.
        "spring.security.oauth2.client.provider.auth0.authorization-uri=https://example.test/authorize",
        "spring.security.oauth2.client.provider.auth0.token-uri=https://example.test/oauth/token",
        "spring.security.oauth2.client.provider.auth0.jwk-set-uri=https://example.test/.well-known/jwks.json",
        "spring.security.oauth2.client.provider.auth0.user-info-uri=https://example.test/userinfo",
        "spring.security.oauth2.client.provider.auth0.user-name-attribute=sub",
        "beancounter.admin.client.bearer-token="
    ]
)
class AdminBootContextTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `context loads with SBA server + bearer-token provider beans`() {
        assertThat(context.getBean(BearerTokenHttpHeadersProvider::class.java)).isNotNull
        assertThat(context.getBean(SecurityConfig::class.java)).isNotNull
    }
}