package com.beancounter.marketdata.assets.owned

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
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
 * The `/assets/me` routes resolve the owner from the signed-in user, so a service (M2M)
 * token, which has no user, is refused rather than served an empty or failing answer.
 */
@SpringMvcDbTest
internal class OwnedAssetAuthTest
    @Autowired
    private constructor(
        private val mockMvc: MockMvc,
        private val mockAuthConfig: MockAuthConfig
    ) {
        @MockitoBean
        private lateinit var jwtDecoder: JwtDecoder

        @MockitoBean
        private lateinit var ownedAssetService: OwnedAssetService

        @Test
        fun `should return 403 when a system token lists owned assets`() {
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/assets/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(systemOnlyToken()))
                ).andExpect(MockMvcResultMatchers.status().isForbidden)

            assertThat(Mockito.mockingDetails(ownedAssetService).invocations).isEmpty()
        }

        @Test
        fun `should list owned assets when a user token calls`() {
            `when`(ownedAssetService.findByOwner()).thenReturn(AssetUpdateResponse(emptyMap()))

            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/assets/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(MockMvcResultMatchers.status().isOk)
        }

        private fun systemOnlyToken(): Jwt =
            Jwt
                .withTokenValue("system-only")
                .header("alg", "none")
                .subject("system-only")
                .claim("scope", "${AuthConstants.APP_NAME} ${AuthConstants.SYSTEM}")
                .expiresAt(Instant.now().plusSeconds(60))
                .build()
    }