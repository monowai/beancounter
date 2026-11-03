package com.beancounter.marketdata.assets

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
 * `PATCH /assets/{assetId}/status` deactivates an asset for every user (an inactive
 * asset drops out of price refresh), so only an admin-scoped token may change it.
 */
@SpringMvcDbTest
internal class AssetStatusAuthTest
    @Autowired
    private constructor(
        private val mockMvc: MockMvc,
        private val mockAuthConfig: MockAuthConfig
    ) {
        @MockitoBean
        private lateinit var jwtDecoder: JwtDecoder

        @MockitoBean
        private lateinit var assetService: AssetService

        @Test
        fun `should return 403 and not change status when token has only user scope`() {
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .patch("/assets/$ASSET_ID/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INACTIVE)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(userOnlyToken()))
                ).andExpect(MockMvcResultMatchers.status().isForbidden)

            assertThat(Mockito.mockingDetails(assetService).invocations).isEmpty()
        }

        @Test
        fun `should reach the status update when token has admin scope`() {
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .patch("/assets/$ASSET_ID/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INACTIVE)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                ).andReturn()

            assertThat(
                Mockito
                    .mockingDetails(assetService)
                    .invocations
                    .map { it.method.name }
            ).contains("updateStatus")
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
            const val ASSET_ID = "status-auth-asset"
            const val INACTIVE = """{"status":"Inactive"}"""
        }
    }