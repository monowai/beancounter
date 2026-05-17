package com.beancounter.admin

import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver

/**
 * Auth0 issues a generic OIDC token with no `beancounter:*` permissions
 * unless the authorization request carries an `audience` query param
 * pointing at the Beancounter API. Without that, the post-login
 * authority check (SCOPE_beancounter:admin) fails with 403 even for
 * users assigned the admin role.
 */
@SpringBootTest(
    properties = [
        "spring.security.oauth2.client.registration.auth0.client-id=test-client",
        "spring.security.oauth2.client.registration.auth0.client-secret=test-secret",
        "spring.security.oauth2.client.registration.auth0.scope=openid,profile,email,beancounter:admin",
        "spring.security.oauth2.client.registration.auth0.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.auth0.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.provider.auth0.authorization-uri=https://example.test/authorize",
        "spring.security.oauth2.client.provider.auth0.token-uri=https://example.test/oauth/token",
        "spring.security.oauth2.client.provider.auth0.jwk-set-uri=https://example.test/.well-known/jwks.json",
        "spring.security.oauth2.client.provider.auth0.user-info-uri=https://example.test/userinfo",
        "spring.security.oauth2.client.provider.auth0.user-name-attribute=sub",
        "beancounter.admin.client.bearer-token="
    ]
)
class AudienceResolverTest {
    @Autowired
    private lateinit var resolver: OAuth2AuthorizationRequestResolver

    @Test
    fun `authorization request injects audience and preserves scopes`() {
        val request: HttpServletRequest =
            MockHttpServletRequest().apply {
                requestURI = "/oauth2/authorization/auth0"
                servletPath = "/oauth2/authorization/auth0"
            }

        val resolved = resolver.resolve(request)

        assertThat(resolved).isNotNull
        assertThat(resolved!!.additionalParameters)
            .containsEntry("audience", "https://holdsworth.app")
        assertThat(resolved.scopes)
            .contains("openid", "profile", "email", "beancounter:admin")
    }
}