package com.beancounter.marketdata.apikey

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.contracts.ApiKeyRequest
import com.beancounter.common.contracts.ApiKeyResponse
import com.beancounter.common.contracts.ApiKeyTokenRequest
import com.beancounter.common.contracts.DeviceKeyRequest
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.WidgetKeyRequest
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.BcMvcHelper
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
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

private const val API_KEYS = "/me/api-keys"
private const val DEVICE_KEYS = "$API_KEYS/device"
private const val WIDGET_KEYS = "$API_KEYS/widget"
private const val TOKEN_ENDPOINT = "/api-keys/token"
private const val DEVICE_AUDIENCE = "urn:beancounter:device-token"

/**
 * Widget keys: a home-screen widget cannot prompt for biometrics, so it holds its own
 * device-bound key, registered under the phone's device key, whose tokens carry only
 * `beancounter:widget`. That scope reaches the read routes in `auth.widget-routes` and
 * nothing else.
 */
@SpringMvcDbTest
internal class WidgetKeyTest {
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

    private fun keyDerived(
        token: Jwt,
        keyId: String
    ): Jwt =
        Jwt
            .withTokenValue(token.tokenValue)
            .headers { it.putAll(token.headers) }
            .claims { it.putAll(token.claims) }
            .issuer(authConfig.bcIssuerUri)
            .claim(BcTokenIssuer.API_KEY_ID_CLAIM, keyId)
            .build()

    private fun newKey(): ECKey = ECKeyGenerator(Curve.P_256).generate()

    private fun spki(key: ECKey): String = Base64.getEncoder().encodeToString(key.toECPublicKey().encoded)

    private fun json(
        token: Jwt,
        path: String,
        body: Any
    ): ResultActions =
        mockMvc.perform(
            post(path)
                .with(jwt().jwt(token))
                .with(csrf())
                .content(objectMapper.writeValueAsBytes(body))
                .contentType(MediaType.APPLICATION_JSON)
        )

    private fun idOf(result: ResultActions): String =
        objectMapper
            .readValue(result.andReturn().response.contentAsString, ApiKeyResponse::class.java)
            .data.id

    private fun deviceKey(token: Jwt): String =
        idOf(
            json(
                token,
                DEVICE_KEYS,
                DeviceKeyRequest(name = "phone", publicKey = spki(newKey()))
            ).andExpect(status().isOk)
        )

    private fun registerWidget(
        token: Jwt,
        deviceKeyId: String,
        key: ECKey = newKey()
    ): ResultActions =
        json(token, WIDGET_KEYS, WidgetKeyRequest(name = "widget", publicKey = spki(key), deviceKeyId = deviceKeyId))

    private fun widgetKey(
        token: Jwt,
        deviceKeyId: String,
        key: ECKey = newKey()
    ): String = idOf(registerWidget(token, deviceKeyId, key).andExpect(status().isOk))

    private fun revokedAt(id: String): Instant? = apiKeyRepository.findById(id).orElseThrow().revokedAt

    private fun assertion(
        key: ECKey,
        keyId: String
    ): String {
        val now = Instant.now()
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(keyId)
                .subject(keyId)
                .audience(DEVICE_AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(2))))
                .jwtID(UUID.randomUUID().toString())
                .build()
        val signed = SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims)
        signed.sign(ECDSASigner(key))
        return signed.serialize()
    }

    // The token the widget really holds: minted by the assertion exchange.
    private fun widgetToken(
        id: String,
        remoteAddr: String,
        token: Jwt = registeredToken(id)
    ): Jwt {
        val key = newKey()
        val widgetId = widgetKey(token, deviceKey(token), key)
        val result =
            mockMvc
                .perform(
                    post(TOKEN_ENDPOINT)
                        .with(csrf())
                        .with { request -> request.apply { this.remoteAddr = remoteAddr } }
                        .content(
                            objectMapper.writeValueAsBytes(ApiKeyTokenRequest(assertion = assertion(key, widgetId)))
                        ).contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()
        val minted =
            SignedJWT
                .parse(objectMapper.readValue(result.response.contentAsString, OpenIdResponse::class.java).token)
                .jwtClaimsSet
        return Jwt
            .withTokenValue("widget")
            .header("alg", "RS256")
            .claims { claims ->
                minted.claims.forEach { (name, value) ->
                    claims[name] = if (value is Date) value.toInstant() else value
                }
            }.build()
    }

    @Test
    fun `should register a widget key under the caller's device key`() {
        val token = registeredToken("widget-register")
        val parent = deviceKey(token)
        val key = newKey()

        val stored = apiKeyRepository.findById(widgetKey(token, parent, key)).orElseThrow()

        assertThat(stored.parentId).isEqualTo(parent)
        assertThat(stored.publicKey).isEqualTo(spki(key))
        assertThat(stored.prefix).startsWith("bcd_")
        assertThat(stored.scopes).isEqualTo(AuthConstants.WIDGET)
        assertThat(stored.owner.email).isEqualTo("widget-register@testing.com")
    }

    @Test
    fun `should refuse widget registration from a key-derived token`() {
        val token = registeredToken("widget-derived")
        val parent = deviceKey(token)

        registerWidget(keyDerived(token, parent), parent).andExpect(status().isForbidden)
    }

    @Test
    fun `should refuse another user's device key as the parent`() {
        val other = deviceKey(registeredToken("widget-other-owner"))

        registerWidget(registeredToken("widget-caller"), other).andExpect(status().isNotFound)
    }

    @Test
    fun `should refuse a revoked device key as the parent`() {
        val token = registeredToken("widget-revoked-parent")
        val parent = deviceKey(token)
        mockMvc.perform(delete("$API_KEYS/$parent").with(jwt().jwt(token)).with(csrf())).andExpect(status().isOk)

        registerWidget(token, parent).andExpect(status().isBadRequest)
    }

    @Test
    fun `should refuse a widget key or a plain api key as the parent`() {
        val token = registeredToken("widget-bad-parent")
        val widget = widgetKey(token, deviceKey(token))
        val plain =
            objectMapper
                .readTree(
                    json(
                        token,
                        API_KEYS,
                        ApiKeyRequest(name = "plain")
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                ).path("data")
                .path("id")
                .asString()

        registerWidget(token, widget).andExpect(status().isBadRequest)
        registerWidget(token, plain).andExpect(status().isBadRequest)
    }

    @Test
    fun `should revoke the previous widget key when a device registers another`() {
        val token = registeredToken("widget-replace")
        val parent = deviceKey(token)
        val first = widgetKey(token, parent)

        val second = widgetKey(token, parent)

        assertThat(revokedAt(first)).isNotNull()
        assertThat(revokedAt(second)).isNull()
    }

    @Test
    fun `should revoke the widget key when its device key is revoked by id`() {
        val token = registeredToken("widget-cascade-id")
        val parent = deviceKey(token)
        val widget = widgetKey(token, parent)

        mockMvc.perform(delete("$API_KEYS/$parent").with(jwt().jwt(token)).with(csrf())).andExpect(status().isOk)

        assertThat(revokedAt(widget)).isNotNull()
    }

    @Test
    fun `should revoke the widget key when its device key revokes itself`() {
        val token = registeredToken("widget-cascade-current")
        val parent = deviceKey(token)
        val widget = widgetKey(token, parent)

        mockMvc
            .perform(delete("$API_KEYS/current").with(jwt().jwt(keyDerived(token, parent))).with(csrf()))
            .andExpect(status().isOk)

        assertThat(revokedAt(parent)).isNotNull()
        assertThat(revokedAt(widget)).isNotNull()
    }

    @Test
    fun `should refuse a widget key whose device key was revoked outside the revoke path`() {
        val token = registeredToken("widget-orphan")
        val parent = deviceKey(token)
        val key = newKey()
        val widget = widgetKey(token, parent, key)
        val device = apiKeyRepository.findById(parent).orElseThrow()
        device.revokedAt = Instant.now()
        apiKeyRepository.save(device)

        mockMvc
            .perform(
                post(TOKEN_ENDPOINT)
                    .with(csrf())
                    .with { request -> request.apply { this.remoteAddr = "10.11.0.9" } }
                    .content(objectMapper.writeValueAsBytes(ApiKeyTokenRequest(assertion = assertion(key, widget))))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `should mint a widget token whose only scope is widget`() {
        val widget = widgetToken("widget-exchange", "10.11.0.1")

        assertThat(widget.getClaimAsString("scope")).isEqualTo(AuthConstants.WIDGET)
        assertThat(widget.getClaimAsString("email")).isEqualTo("widget-exchange@testing.com")
    }

    @Test
    fun `should never put the widget scope on an ordinary api key or device key`() {
        val token = registeredToken("widget-not-issuable")
        val holdsWidget =
            Jwt
                .withTokenValue(token.tokenValue)
                .headers { it.putAll(token.headers) }
                .claims { it.putAll(token.claims) }
                .claim("scope", "beancounter beancounter:user ${AuthConstants.WIDGET}")
                .build()

        json(holdsWidget, API_KEYS, ApiKeyRequest(name = "asks", scopes = listOf(AuthConstants.WIDGET)))
            .andExpect(status().isBadRequest)
        val device =
            idOf(
                json(
                    holdsWidget,
                    DEVICE_KEYS,
                    DeviceKeyRequest(name = "phone", publicKey = spki(newKey()))
                ).andExpect(status().isOk)
            )

        assertThat(
            apiKeyRepository
                .findById(device)
                .orElseThrow()
                .scopes
                .split(" ")
        ).doesNotContain(AuthConstants.WIDGET)
    }

    private fun call(
        widget: Jwt,
        request: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder,
        expect: ResultMatcher
    ) {
        mockMvc.perform(request.with(jwt().jwt(widget)).with(csrf())).andExpect(expect)
    }

    @Test
    fun `should let a widget token read the watchlist and its alerts`() {
        val widget = widgetToken("widget-reads", "10.11.0.2")

        call(widget, get("/watchlist"), status().isOk)
        call(widget, get("/watchlist/alerts"), status().isOk)
    }

    @Test
    fun `should let a widget token make the reads svc-position forwards for net worth`() {
        val owner = registeredToken("widget-net-worth-reads")
        val portfolio =
            BcMvcHelper
                .portfolioCreate(PortfolioInput(code = "WIDGET-NW"), mockMvc, owner)
                .data
                .first()
        val widget = widgetToken("widget-net-worth-reads", "10.11.0.4", owner)

        call(widget, get("/portfolios"), status().isOk)
        call(widget, get("/trns/portfolio/${portfolio.id}"), status().isOk)
        call(
            widget,
            post(
                "/prices"
            ).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(PriceRequest())),
            status().isOk
        )
        call(
            widget,
            post("/fx").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(FxRequest())),
            status().isOk
        )
        call(widget, get("/assets/config"), status().isOk)
        call(
            widget,
            post("/classifications/bulk").contentType(MediaType.APPLICATION_JSON).content("""{"assetIds":[]}"""),
            status().isOk
        )
    }

    @Test
    fun `should refuse a widget token a route the gate lets through but the handler does not allow`() {
        val widget = widgetToken("widget-handler-guard", "10.11.0.5")

        // GET /portfolios/{id} is listed, so the gate passes /portfolios/export; its handler refuses.
        call(widget, get("/portfolios/export"), status().isForbidden)
    }

    @Test
    fun `should refuse a widget token a watchlist write and the api key list`() {
        val widget = widgetToken("widget-writes", "10.11.0.3")

        call(
            widget,
            post("/watchlist/items").contentType(MediaType.APPLICATION_JSON).content("""{"assetId":"any"}"""),
            status().isForbidden
        )
        call(widget, get(API_KEYS), status().isForbidden)
        call(widget, delete("$API_KEYS/current"), status().isForbidden)
    }
}