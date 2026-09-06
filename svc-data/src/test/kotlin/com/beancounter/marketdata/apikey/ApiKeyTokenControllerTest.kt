package com.beancounter.marketdata.apikey

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.contracts.ApiKeyCreatedResponse
import com.beancounter.common.contracts.ApiKeyRequest
import com.beancounter.common.contracts.ApiKeyTokenRequest
import com.beancounter.common.model.ApiKey
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val API_KEYS_ROOT = "/me/api-keys"
private const val TOKEN_ENDPOINT = "/api-keys/token"
private const val JWKS_ENDPOINT = "/.well-known/jwks.json"

/**
 * Behaviour tests for the API-key -> JWT token-exchange endpoint (phase 2 -
 * bc-claude/MCP.md): a valid, unrevoked, unexpired key mints a short-lived
 * JWT signed by svc-data's own bc-issuer key, published at [JWKS_ENDPOINT].
 * The endpoint is intentionally unauthenticated - it authenticates by the
 * key itself (see ControllerAuthorizationTest's allowlist).
 */
@SpringMvcDbTest
internal class ApiKeyTokenControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var apiKeyRepository: ApiKeyRepository

    @Autowired
    private lateinit var systemUserRepository: SystemUserRepository

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private fun registeredToken(id: String): Jwt =
        registerUser(
            mockMvc,
            mockAuthConfig.getUserToken(
                SystemUser(
                    id = id,
                    email = "$id@testing.com",
                    auth0 = "auth0|$id"
                )
            )
        )

    private fun createKey(
        token: Jwt,
        request: ApiKeyRequest = ApiKeyRequest(name = "test key")
    ): ApiKeyCreatedResponse {
        val result =
            mockMvc
                .perform(
                    post(API_KEYS_ROOT)
                        .with(jwt().jwt(token))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(request))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()
        return objectMapper.readValue(
            result.response.contentAsString,
            ApiKeyCreatedResponse::class.java
        )
    }

    // TokenRateLimiter is a Spring singleton shared by every test method in
    // this class (SpringMvcDbTest keeps one context per class), and MockMvc
    // defaults every request to the same 127.0.0.1 remote address. Without
    // a distinct IP per test, one test's calls would count against the
    // next test's rate-limit window. `remoteAddr` is therefore required,
    // not defaulted - each test picks its own address.
    private fun exchange(
        apiKey: String,
        remoteAddr: String
    ): ResultActions =
        mockMvc.perform(
            post(TOKEN_ENDPOINT)
                .with(csrf())
                .with { request -> request.apply { this.remoteAddr = remoteAddr } }
                .content(objectMapper.writeValueAsBytes(ApiKeyTokenRequest(apiKey = apiKey)))
                .contentType(MediaType.APPLICATION_JSON)
        )

    private fun ownerOf(id: String): SystemUser =
        systemUserRepository.findByEmail("$id@testing.com").orElseThrow {
            IllegalStateException("Owner not registered")
        }

    @Test
    fun `exchange returns signed jwt for valid key`() {
        val token = registeredToken("token-exchange-ok")
        val created = createKey(token)
        val owner = ownerOf("token-exchange-ok")

        val result = exchange(created.apiKey, "10.1.0.1").andExpect(status().isOk).andReturn()
        val response = objectMapper.readValue(result.response.contentAsString, OpenIdResponse::class.java)

        assertThat(response.type).isEqualTo("Bearer")
        assertThat(response.expiry).isEqualTo(Duration.ofHours(1).seconds)
        assertThat(response.scope).contains("beancounter:user")

        val signedJwt = SignedJWT.parse(response.token)
        val claims = signedJwt.jwtClaimsSet
        assertThat(claims.subject).isEqualTo(owner.id)
        assertThat(claims.getStringClaim("email")).isEqualTo(owner.email)
        assertThat(claims.getStringClaim("scope")).contains("beancounter:user")
        assertThat(claims.getStringClaim("bc:api_key_id")).isEqualTo(created.data.id)
        assertThat(claims.audience).isNotEmpty()
        assertThat(claims.expirationTime.toInstant())
            .isCloseTo(Instant.now().plus(Duration.ofHours(1)), within(30, ChronoUnit.SECONDS))
    }

    @Test
    fun `minted jwt signature verifies against published jwks`() {
        val token = registeredToken("token-exchange-jwks")
        val created = createKey(token)

        val exchangeResult = exchange(created.apiKey, "10.1.0.2").andExpect(status().isOk).andReturn()
        val response = objectMapper.readValue(exchangeResult.response.contentAsString, OpenIdResponse::class.java)

        val jwksResult = mockMvc.perform(get(JWKS_ENDPOINT)).andExpect(status().isOk).andReturn()
        val jwksJson = jwksResult.response.contentAsString
        assertThat(jwksJson).doesNotContain("\"d\"")

        @Suppress("UNCHECKED_CAST")
        val body = objectMapper.readValue(jwksJson, Map::class.java) as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val keys = body["keys"] as List<Map<String, Any>>
        val rsaKey = RSAKey.parse(keys.first())

        val signedJwt = SignedJWT.parse(response.token)
        assertThat(signedJwt.verify(RSASSAVerifier(rsaKey))).isTrue()
    }

    @Test
    fun `revoked key exchange rejected`() {
        val token = registeredToken("token-exchange-revoked")
        val created = createKey(token)
        mockMvc
            .perform(
                delete("$API_KEYS_ROOT/${created.data.id}")
                    .with(jwt().jwt(token))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        exchange(created.apiKey, "10.1.0.3").andExpect(status().isUnauthorized)
    }

    @Test
    fun `expired key exchange rejected`() {
        registeredToken("token-exchange-expired")
        val owner = ownerOf("token-exchange-expired")
        val expiredRawKey = "bc_expiredExpiredExpiredExpired2"
        apiKeyRepository.save(
            ApiKey(
                owner = owner,
                name = "expired",
                prefix = expiredRawKey.take(11),
                keyHash = ApiKeyGenerator.hash(expiredRawKey),
                scopes = "beancounter",
                expiresAt = Instant.now().minusSeconds(60)
            )
        )

        exchange(expiredRawKey, "10.1.0.4").andExpect(status().isUnauthorized)
    }

    @Test
    fun `unknown key exchange rejected`() {
        exchange("bc_totallyUnknownKeyThatWasNeverIssued99", "10.1.0.5").andExpect(status().isUnauthorized)
    }

    @Test
    fun `exchange touches lastUsedAt`() {
        val token = registeredToken("token-exchange-touch")
        val created = createKey(token)
        assertThat(apiKeyRepository.findById(created.data.id).orElseThrow().lastUsedAt).isNull()

        exchange(created.apiKey, "10.1.0.6").andExpect(status().isOk)

        val stored = apiKeyRepository.findById(created.data.id).orElseThrow()
        assertThat(stored.lastUsedAt).isNotNull()
    }

    @Test
    fun `rate limit returns 429 when window exhausted`() {
        val token = registeredToken("token-exchange-ratelimit")
        val created = createKey(token)

        repeat(10) {
            exchange(created.apiKey, "10.1.0.7").andExpect(status().isOk)
        }
        exchange(created.apiKey, "10.1.0.7").andExpect(status().isTooManyRequests)

        // A different caller IP gets its own window - not globally exhausted.
        exchange(created.apiKey, "10.1.0.8").andExpect(status().isOk)
    }
}