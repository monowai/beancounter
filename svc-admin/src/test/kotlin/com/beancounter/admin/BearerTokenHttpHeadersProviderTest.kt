package com.beancounter.admin

import de.codecentric.boot.admin.server.domain.entities.Instance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.OAuth2AccessToken
import java.time.Instant

class BearerTokenHttpHeadersProviderTest {
    private val instance: Instance = mock()
    private val authorizedClientService: OAuth2AuthorizedClientService = mock()

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `attaches bearer from logged-in OIDC user when SecurityContext present`() {
        val accessToken: OAuth2AccessToken =
            mock {
                on { tokenValue } doReturn "user.access.token"
                on { expiresAt } doReturn Instant.now().plusSeconds(3600)
            }
        val client: OAuth2AuthorizedClient =
            mock {
                on { this.accessToken } doReturn accessToken
            }
        whenever(
            authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(eq("auth0"), eq("user@example.com"))
        ).thenReturn(client)

        val auth: OAuth2AuthenticationToken =
            mock {
                on { authorizedClientRegistrationId } doReturn "auth0"
                on { name } doReturn "user@example.com"
            }
        SecurityContextHolder.getContext().authentication = auth

        val provider = BearerTokenHttpHeadersProvider(authorizedClientService, "")

        val headers = provider.getHeaders(instance)

        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer user.access.token")
    }

    @Test
    fun `falls back to cached user token in background poll thread`() {
        val accessToken: OAuth2AccessToken =
            mock {
                on { tokenValue } doReturn "cached.user.token"
                on { expiresAt } doReturn Instant.now().plusSeconds(3600)
            }
        val client: OAuth2AuthorizedClient = mock { on { this.accessToken } doReturn accessToken }
        whenever(authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(eq("auth0"), eq("u")))
            .thenReturn(client)

        val provider = BearerTokenHttpHeadersProvider(authorizedClientService, "")

        // First call inside a request thread — populates the cache.
        val auth: OAuth2AuthenticationToken =
            mock {
                on { authorizedClientRegistrationId } doReturn "auth0"
                on { name } doReturn "u"
            }
        SecurityContextHolder.getContext().authentication = auth
        provider.getHeaders(instance)
        SecurityContextHolder.clearContext()

        // Background-thread call — no auth in context, must use cache.
        val headers = provider.getHeaders(instance)
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer cached.user.token")
    }

    @Test
    fun `falls back to static M2M token when no user has authenticated`() {
        val provider = BearerTokenHttpHeadersProvider(authorizedClientService, "static.m2m.token")

        val headers = provider.getHeaders(instance)

        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer static.m2m.token")
    }

    @Test
    fun `expired cached token does not block static fallback`() {
        // First call seeds the cache with a token whose expiry is already in the past.
        val expiredAccessToken: OAuth2AccessToken =
            mock {
                on { tokenValue } doReturn "expired.user.token"
                on { expiresAt } doReturn Instant.now().minusSeconds(60)
            }
        val client: OAuth2AuthorizedClient = mock { on { this.accessToken } doReturn expiredAccessToken }
        whenever(authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(eq("auth0"), eq("u")))
            .thenReturn(client)

        val auth: OAuth2AuthenticationToken =
            mock {
                on { authorizedClientRegistrationId } doReturn "auth0"
                on { name } doReturn "u"
            }
        SecurityContextHolder.getContext().authentication = auth

        val provider = BearerTokenHttpHeadersProvider(authorizedClientService, "static.m2m.token")
        // Populate the cache. Inside the request thread the current-user lookup still returns
        // the (about-to-be-expired) token — getHeaders will Bearer it. We only care that the
        // next background call falls through to the static fallback.
        provider.getHeaders(instance)
        SecurityContextHolder.clearContext()

        val headers = provider.getHeaders(instance)

        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer static.m2m.token")
    }

    @Test
    fun `omits Authorization when nothing configured and no user`() {
        val provider = BearerTokenHttpHeadersProvider(authorizedClientService, "")

        val headers = provider.getHeaders(instance)

        assertThat(headers.getFirst("Authorization")).isNull()
    }

    private fun <T> eq(value: T): T = org.mockito.ArgumentMatchers.eq(value) ?: value

    @Suppress("UNUSED")
    private val unused: Authentication? = null
}