package com.beancounter.marketdata.news

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.news.eodhd.SentimentPoint
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * MockMvc coverage for the `/news/sentiment` surface: response shape, the `days` default and cap,
 * and the admin gate on the refresh POST.
 */
@SpringMvcDbTest
internal class NewsSentimentControllerTest
    @Autowired
    private constructor(
        private val mockMvc: MockMvc,
        private val mockAuthConfig: MockAuthConfig
    ) {
        @MockitoBean
        private lateinit var jwtDecoder: JwtDecoder

        @MockitoBean
        private lateinit var newsSentimentService: NewsSentimentService

        @Test
        fun `GET sentiment returns points keyed by asset id`() {
            whenever(newsSentimentService.get(listOf("a", "b"), 30))
                .thenReturn(
                    mapOf("a" to listOf(SentimentPoint(LocalDate.parse("2026-10-06"), 27, BigDecimal("0.8937"))))
                )

            mockMvc
                .perform(
                    get("/news/sentiment")
                        .param("assetIds", "a,b")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.a[0].date").value("2026-10-06"))
                .andExpect(jsonPath("$.data.a[0].count").value(27))
                .andExpect(jsonPath("$.data.a[0].normalized").value(0.8937))
        }

        @Test
        fun `GET sentiment caps days at 365`() {
            whenever(newsSentimentService.get(listOf("a"), 365)).thenReturn(emptyMap())

            mockMvc
                .perform(
                    get("/news/sentiment")
                        .param("assetIds", "a")
                        .param("days", "9999")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(status().isOk)

            verify(newsSentimentService).get(eq(listOf("a")), eq(365))
        }

        @Test
        fun `POST refresh is forbidden for a user-only scope token`() {
            mockMvc
                .perform(
                    post("/news/sentiment/refresh")
                        .with(jwt().jwt(userOnlyToken()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `POST refresh returns the refresh counts for an admin token`() {
            whenever(newsSentimentService.refresh())
                .thenReturn(SentimentRefreshResult(assets = 4, calls = 1, rows = 9, failedBatches = 0))

            mockMvc
                .perform(
                    post("/news/sentiment/refresh")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.assets").value(4))
                .andExpect(jsonPath("$.data.calls").value(1))
                .andExpect(jsonPath("$.data.rows").value(9))
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