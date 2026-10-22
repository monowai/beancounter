package com.beancounter.marketdata.apikey

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.contracts.ApiKeyResponse
import com.beancounter.common.contracts.ApiKeyTokenRequest
import com.beancounter.common.contracts.DeviceKeyRequest
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

private const val DEVICE_KEYS = "/me/api-keys/device"
private const val TOKEN_ENDPOINT = "/api-keys/token"
private const val DEVICE_AUDIENCE = "urn:beancounter:device-token"

/**
 * Device-bound keys: the phone registers only the public half of a P-256 key
 * pair held in its secure hardware, and later trades a short-lived ES256
 * assertion signed by that key for a BC JWT. Nothing secret is stored on the
 * server or sent over the wire.
 */
@SpringMvcDbTest
internal class DeviceKeyTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var authConfig: AuthConfig

    @Autowired
    private lateinit var apiKeyRepository: ApiKeyRepository

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private fun registeredToken(id: String): Jwt =
        registerUser(
            mockMvc,
            mockAuthConfig.getUserToken(SystemUser(id = id, email = "$id@testing.com", auth0 = "auth0|$id"))
        )

    private fun spki(key: ECKey): String = Base64.getEncoder().encodeToString(key.toECPublicKey().encoded)

    private fun register(
        token: Jwt,
        publicKey: String
    ): ResultActions =
        mockMvc.perform(
            post(DEVICE_KEYS)
                .with(jwt().jwt(token))
                .with(csrf())
                .content(
                    objectMapper.writeValueAsBytes(DeviceKeyRequest(name = "Beancounter mobile", publicKey = publicKey))
                ).contentType(MediaType.APPLICATION_JSON)
        )

    private fun registered(
        token: Jwt,
        key: ECKey
    ): String {
        val result = register(token, spki(key)).andExpect(status().isOk).andReturn()
        return objectMapper.readValue(result.response.contentAsString, ApiKeyResponse::class.java).data.id
    }

    private fun assertion(
        key: ECKey,
        keyId: String,
        issuer: String = keyId,
        jti: String = UUID.randomUUID().toString(),
        audience: String = DEVICE_AUDIENCE,
        issuedAt: Instant = Instant.now(),
        lifetime: Duration = Duration.ofMinutes(2)
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .subject(issuer)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(lifetime)))
                .jwtID(jti)
                .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims)
        jwt.sign(ECDSASigner(key))
        return jwt.serialize()
    }

    // TokenRateLimiter is shared across the class: each call names its own address.
    private fun exchange(
        assertion: String,
        remoteAddr: String
    ): ResultActions =
        mockMvc.perform(
            post(TOKEN_ENDPOINT)
                .with(csrf())
                .with { request -> request.apply { this.remoteAddr = remoteAddr } }
                .content(objectMapper.writeValueAsBytes(ApiKeyTokenRequest(assertion = assertion)))
                .contentType(MediaType.APPLICATION_JSON)
        )

    private fun newKey(): ECKey = ECKeyGenerator(Curve.P_256).generate()

    @Test
    fun `registers the public half of a P-256 device key`() {
        val token = registeredToken("device-register")
        val key = newKey()

        val id = registered(token, key)

        val stored = apiKeyRepository.findById(id).orElseThrow()
        assertThat(stored.publicKey).isEqualTo(spki(key))
        assertThat(stored.prefix).startsWith("bcd_")
        assertThat(stored.owner.email).isEqualTo("device-register@testing.com")
    }

    @Test
    fun `a device key carries only the scopes the caller holds`() {
        val token = registeredToken("device-scopes")
        val preview =
            Jwt
                .withTokenValue(token.tokenValue)
                .headers { it.putAll(token.headers) }
                .claims { it.putAll(token.claims) }
                .claim("scope", "beancounter beancounter:user beancounter:preview")
                .build()

        val id = registered(preview, newKey())

        assertThat(
            apiKeyRepository
                .findById(id)
                .orElseThrow()
                .scopes
                .split(" ")
        ).containsExactlyInAnyOrder("beancounter", "beancounter:user", "beancounter:preview")
    }

    @Test
    fun `registration needs an account sign-in`() {
        val token = registeredToken("device-register-derived")
        val keyDerived =
            Jwt
                .withTokenValue(token.tokenValue)
                .headers { it.putAll(token.headers) }
                .claims { it.putAll(token.claims) }
                .issuer(authConfig.bcIssuerUri)
                .build()

        register(keyDerived, spki(newKey())).andExpect(status().isForbidden)
    }

    @Test
    fun `registration rejects a key that is not P-256`() {
        val token = registeredToken("device-register-rsa")
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val p384 = ECKeyGenerator(Curve.P_384).generate()

        register(token, Base64.getEncoder().encodeToString(rsa.public.encoded)).andExpect(status().isBadRequest)
        register(token, spki(p384)).andExpect(status().isBadRequest)
        register(token, "not-a-key").andExpect(status().isBadRequest)
    }

    @Test
    fun `a signed assertion buys a BC token for that device key`() {
        val key = newKey()
        val id = registered(registeredToken("device-exchange"), key)

        val result = exchange(assertion(key, id), "10.9.0.1").andExpect(status().isOk).andReturn()

        val response = objectMapper.readValue(result.response.contentAsString, OpenIdResponse::class.java)
        val minted = SignedJWT.parse(response.token).jwtClaimsSet
        assertThat(minted.getStringClaim("bc:api_key_id")).isEqualTo(id)
        val stored = apiKeyRepository.findById(id).orElseThrow()
        assertThat(minted.subject).isEqualTo(stored.owner.id)
        assertThat(stored.owner.email).isEqualTo("device-exchange@testing.com")
        assertThat(stored.lastUsedAt).isNotNull()
    }

    @Test
    fun `an assertion is good for one exchange only`() {
        val key = newKey()
        val id = registered(registeredToken("device-replay"), key)
        val once = assertion(key, id)

        exchange(once, "10.9.0.2").andExpect(status().isOk)
        exchange(once, "10.9.0.2").andExpect(status().isUnauthorized)
    }

    @Test
    fun `an assertion signed by another key is refused`() {
        val id = registered(registeredToken("device-forged"), newKey())

        exchange(assertion(newKey(), id), "10.9.0.3").andExpect(status().isUnauthorized)
    }

    @Test
    fun `an expired or long-lived assertion is refused`() {
        val key = newKey()
        val id = registered(registeredToken("device-lifetime"), key)

        exchange(assertion(key, id, issuedAt = Instant.now().minus(Duration.ofMinutes(10))), "10.9.0.4")
            .andExpect(status().isUnauthorized)
        exchange(assertion(key, id, lifetime = Duration.ofHours(1)), "10.9.0.4")
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `an assertion for another audience is refused`() {
        val key = newKey()
        val id = registered(registeredToken("device-audience"), key)

        exchange(assertion(key, id, audience = "https://holdsworth.app"), "10.9.0.5").andExpect(status().isUnauthorized)
    }

    @Test
    fun `an assertion must name its own key as issuer`() {
        val key = newKey()
        val id = registered(registeredToken("device-issuer"), key)

        exchange(assertion(key, id, issuer = "someone-else"), "10.9.0.8").andExpect(status().isUnauthorized)
    }

    @Test
    fun `a revoked device key buys nothing`() {
        val token = registeredToken("device-revoked")
        val key = newKey()
        val id = registered(token, key)
        mockMvc
            .perform(delete("/me/api-keys/$id").with(jwt().jwt(token)).with(csrf()))
            .andExpect(status().isOk)

        exchange(assertion(key, id), "10.9.0.6").andExpect(status().isUnauthorized)
    }

    @Test
    fun `a device key cannot be presented as a raw api key`() {
        val key = newKey()
        registered(registeredToken("device-as-raw"), key)

        mockMvc
            .perform(
                post(TOKEN_ENDPOINT)
                    .with(csrf())
                    .with { request -> request.apply { remoteAddr = "10.9.0.9" } }
                    .content(objectMapper.writeValueAsBytes(ApiKeyTokenRequest(apiKey = "device-spki:${spki(key)}")))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `an assertion without an audience is refused`() {
        val key = newKey()
        val id = registered(registeredToken("device-no-aud"), key)
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(id)
                .subject(id)
                .issueTime(Date())
                .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(2))))
                .jwtID(UUID.randomUUID().toString())
                .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(id).build(), claims)
        jwt.sign(ECDSASigner(key))

        exchange(jwt.serialize(), "10.9.0.10").andExpect(status().isUnauthorized)
    }

    @Test
    fun `an assertion cannot name a plain api key`() {
        val token = registeredToken("device-plain")
        val created =
            mockMvc
                .perform(
                    post("/me/api-keys")
                        .with(jwt().jwt(token))
                        .with(csrf())
                        .content("""{"name":"plain"}""")
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()
        val id = objectMapper.readTree(created.response.contentAsString)["data"]["id"].asText()

        exchange(assertion(newKey(), id), "10.9.0.7").andExpect(status().isUnauthorized)
    }
}