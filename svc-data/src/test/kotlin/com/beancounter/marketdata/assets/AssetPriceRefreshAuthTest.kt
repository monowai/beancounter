package com.beancounter.marketdata.assets

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.providers.PriceRefresh
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant

/**
 * `POST /assets/price/refresh` refreshes prices for every asset in the system,
 * so only an admin-scoped token may fire it.
 */
@SpringMvcDbTest
internal class AssetPriceRefreshAuthTest
    @Autowired
    private constructor(
        private val mockMvc: MockMvc,
        private val mockAuthConfig: MockAuthConfig
    ) {
        @MockitoBean
        private lateinit var jwtDecoder: JwtDecoder

        @MockitoBean
        private lateinit var priceRefresh: PriceRefresh

        @Test
        fun `should return 403 and not refresh when token has only user scope`() {
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/assets/price/refresh")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(userOnlyToken()))
                ).andExpect(MockMvcResultMatchers.status().isForbidden)

            assertThat(Mockito.mockingDetails(priceRefresh).invocations).isEmpty()
        }

        @Test
        fun `should refresh prices when token has admin scope`() {
            Mockito.`when`(priceRefresh.updatePrices()).thenReturn(REFRESHED)

            val body =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/assets/price/refresh")
                            .with(
                                SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken())
                            )
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturn()
                    .response.contentAsString

            assertThat(body).isEqualTo(REFRESHED.toString())
        }

        private fun userOnlyToken(): Jwt =
            Jwt
                .withTokenValue("user-only")
                .header("alg", "none")
                .subject("user-only")
                .claim("scope", "${AuthConstants.APP_NAME} ${AuthConstants.USER}")
                .expiresAt(Instant.now().plusSeconds(60))
                .build()

        private companion object {
            const val REFRESHED = 3
        }
    }