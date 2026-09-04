package com.beancounter.marketdata.apikey

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.ApiKeyCreatedResponse
import com.beancounter.common.contracts.ApiKeyRequest
import com.beancounter.common.contracts.ApiKeyResponse
import com.beancounter.common.contracts.ApiKeysResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.ApiKey
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

private const val API_KEYS_ROOT = "/me/api-keys"

/**
 * Behaviour tests for BC-issued API keys (phase 1 - bc-claude/MCP.md):
 * the create/list/revoke management endpoints, plus the service-level
 * [ApiKeyService.verify] that phase 2's token-exchange endpoint will call.
 */
@SpringMvcDbTest
internal class ApiKeyControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var apiKeyRepository: ApiKeyRepository

    @Autowired
    private lateinit var apiKeyService: ApiKeyService

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

    @Test
    fun `create returns bc_-prefixed key exactly once`() {
        val token = registeredToken("apikey-create")
        val created = createKey(token)

        assertThat(created.apiKey).startsWith("bc_")
        assertThat(created.apiKey).hasSize(35)
        assertThat(created.data.prefix).isEqualTo(created.apiKey.substring(0, 11))
        assertThat(objectMapper.writeValueAsString(created)).doesNotContain("keyHash")
    }

    @Test
    fun `stored key is hashed`() {
        val token = registeredToken("apikey-hash")
        val created = createKey(token)

        val stored =
            apiKeyRepository.findById(created.data.id).orElseThrow {
                IllegalStateException("API key not persisted")
            }
        assertThat(stored.keyHash).isNotEqualTo(created.apiKey)
        assertThat(stored.keyHash).hasSize(64)
        assertThat(stored.keyHash).matches("[0-9a-f]{64}")
    }

    @Test
    fun `list returns only callers keys without secrets`() {
        val tokenA = registeredToken("apikey-list-a")
        val tokenB = registeredToken("apikey-list-b")
        val createdA = createKey(tokenA)
        createKey(tokenB)

        val result =
            mockMvc
                .perform(
                    get(API_KEYS_ROOT)
                        .with(jwt().jwt(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        assertThat(result.response.contentAsString).doesNotContain("apiKey\"")
        assertThat(result.response.contentAsString).doesNotContain("keyHash")

        val response =
            objectMapper.readValue(
                result.response.contentAsString,
                ApiKeysResponse::class.java
            )
        assertThat(response.data).hasSize(1)
        assertThat(response.data.first().id).isEqualTo(createdA.data.id)
    }

    @Test
    fun `revoke sets revokedAt and is idempotent`() {
        val token = registeredToken("apikey-revoke")
        val created = createKey(token)

        val first =
            mockMvc
                .perform(
                    delete("$API_KEYS_ROOT/${created.data.id}")
                        .with(jwt().jwt(token))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()
        val firstView =
            objectMapper
                .readValue(first.response.contentAsString, ApiKeyResponse::class.java)
                .data
        assertThat(firstView.revokedAt).isNotNull()

        val second =
            mockMvc
                .perform(
                    delete("$API_KEYS_ROOT/${created.data.id}")
                        .with(jwt().jwt(token))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()
        val secondView =
            objectMapper
                .readValue(second.response.contentAsString, ApiKeyResponse::class.java)
                .data
        assertThat(secondView.revokedAt).isEqualTo(firstView.revokedAt)
    }

    @Test
    fun `cannot revoke another users key`() {
        val ownerToken = registeredToken("apikey-owner")
        val otherToken = registeredToken("apikey-other")
        val created = createKey(ownerToken)

        mockMvc
            .perform(
                delete("$API_KEYS_ROOT/${created.data.id}")
                    .with(jwt().jwt(otherToken))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().is4xxClientError)
    }

    @Test
    fun `scope escalation rejected`() {
        val token = registeredToken("apikey-escalate")

        mockMvc
            .perform(
                post(API_KEYS_ROOT)
                    .with(jwt().jwt(token))
                    .with(csrf())
                    .content(
                        objectMapper.writeValueAsBytes(
                            ApiKeyRequest(name = "admin-key", scopes = listOf("beancounter:admin"))
                        )
                    ).contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isBadRequest)

        mockMvc
            .perform(
                post(API_KEYS_ROOT)
                    .with(jwt().jwt(token))
                    .with(csrf())
                    .content(
                        objectMapper.writeValueAsBytes(
                            ApiKeyRequest(name = "system-key", scopes = listOf("beancounter:system"))
                        )
                    ).contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `default scopes applied`() {
        val token = registeredToken("apikey-defaults")
        val created = createKey(token, ApiKeyRequest(name = "default-scope-key"))

        assertThat(created.data.scopes)
            .containsExactlyInAnyOrder("beancounter", "beancounter:user", "beancounter:ai")
    }

    @Test
    fun `verify accepts valid key and touches lastUsedAt`() {
        val token = registeredToken("apikey-verify-ok")
        val created = createKey(token)

        val verified = apiKeyService.verify(created.apiKey)
        assertThat(verified.lastUsedAt).isNotNull()

        val stored = apiKeyRepository.findById(created.data.id).orElseThrow()
        assertThat(stored.lastUsedAt).isNotNull()
    }

    @Test
    fun `verify rejects revoked and expired keys`() {
        val token = registeredToken("apikey-verify-bad")
        val created = createKey(token)

        mockMvc
            .perform(
                delete("$API_KEYS_ROOT/${created.data.id}")
                    .with(jwt().jwt(token))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        assertThatThrownBy { apiKeyService.verify(created.apiKey) }
            .isInstanceOf(UnauthorizedException::class.java)

        val owner =
            systemUserRepository.findByEmail("apikey-verify-bad@testing.com").orElseThrow {
                IllegalStateException("Owner not registered")
            }
        val expiredRawKey = "bc_expiredExpiredExpiredExpired1"
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

        assertThatThrownBy { apiKeyService.verify(expiredRawKey) }
            .isInstanceOf(UnauthorizedException::class.java)
    }
}