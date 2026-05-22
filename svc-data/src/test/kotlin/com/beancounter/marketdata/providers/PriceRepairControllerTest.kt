package com.beancounter.marketdata.providers

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.marketdata.SpringMvcDbTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant

/**
 * Integration tests for `POST /prices/{assetId}/repair-splits`.
 *
 * Writes provider-derived split factors back onto stored price rows, so the
 * endpoint is gated to admin / system scope — a regular user-scope token
 * (i.e. the JWT a bc-view session holds for a non-admin login) must not be
 * able to invoke it.
 */
@SpringMvcDbTest
internal class PriceRepairControllerTest
    @Autowired
    private constructor(
        private val mockMvc: MockMvc,
        private val mockAuthConfig: MockAuthConfig
    ) {
        @MockitoBean
        private lateinit var jwtDecoder: JwtDecoder

        @Test
        fun `repair-splits is forbidden for a user-only scope token`() {
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/prices/asset-1/repair-splits")
                        .with(
                            SecurityMockMvcRequestPostProcessors.jwt().jwt(userOnlyToken())
                        ).contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `repair-splits is allowed for the default mock token (carries admin scope)`() {
            // mockAuthConfig.getUserToken() grants beancounter + user + admin
            // by design (TokenUtils.getUserToken). Verify the @PreAuthorize on
            // the controller accepts that token shape so admin-flow tests in
            // bc-view's MockMvc layer keep working.
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/prices/asset-1/repair-splits")
                        .with(
                            SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken())
                        ).contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(
                    // The gate passes the request through — downstream may 404 on a
                    // non-existent asset, which is fine. We're only asserting the
                    // @PreAuthorize check doesn't reject (not 401/403).
                    MockMvcResultMatchers.status().`is`(404)
                )
        }

        private fun userOnlyToken(): Jwt =
            Jwt
                .withTokenValue("user-only")
                .header("alg", "none")
                .subject("user-only")
                .claim("scope", "${AuthConstants.APP_NAME} ${AuthConstants.USER}")
                .expiresAt(Instant.now().plusSeconds(60))
                .build()
    }