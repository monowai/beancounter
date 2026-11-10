package com.beancounter.marketdata.news

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.news.eodhd.SentimentPoint
import com.beancounter.marketdata.news.sentiment.MarketSentimentPoint
import com.beancounter.marketdata.news.sentiment.MarketSentimentRefreshResult
import com.beancounter.marketdata.news.sentiment.MarketSentimentSeries
import com.beancounter.marketdata.news.sentiment.MarketSentimentService
import com.beancounter.marketdata.news.sentiment.NewsSentimentService
import com.beancounter.marketdata.news.sentiment.SentimentRefreshResult
import com.beancounter.marketdata.news.sentiment.SentimentScopeKind
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

        @MockitoBean
        private lateinit var marketSentimentService: MarketSentimentService

        @Test
        fun `GET market sentiment returns one series per scope with daily counts`() {
            val point = MarketSentimentPoint(LocalDate.parse("2026-11-09"), 4, 3, 2, BigDecimal("0.1250"))
            whenever(marketSentimentService.get(30))
                .thenReturn(
                    listOf(
                        MarketSentimentSeries(SentimentScopeKind.TOPIC, "inflation", listOf(point)),
                        MarketSentimentSeries(SentimentScopeKind.SECTOR, "technology", emptyList())
                    )
                )

            mockMvc
                .perform(get("/news/sentiment/market").with(jwt().jwt(mockAuthConfig.getUserToken())))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data[0].kind").value("TOPIC"))
                .andExpect(jsonPath("$.data[0].name").value("inflation"))
                .andExpect(jsonPath("$.data[0].points[0].date").value("2026-11-09"))
                .andExpect(jsonPath("$.data[0].points[0].positive").value(4))
                .andExpect(jsonPath("$.data[0].points[0].neutral").value(3))
                .andExpect(jsonPath("$.data[0].points[0].negative").value(2))
                .andExpect(jsonPath("$.data[0].points[0].polarity").value(0.125))
                .andExpect(jsonPath("$.data[1].kind").value("SECTOR"))
        }

        @Test
        fun `GET market sentiment caps days at 365`() {
            whenever(marketSentimentService.get(365)).thenReturn(emptyList())

            mockMvc
                .perform(
                    get("/news/sentiment/market")
                        .param("days", "9999")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(status().isOk)

            verify(marketSentimentService).get(365)
        }

        @Test
        fun `POST market refresh is forbidden for a user-only scope token`() {
            mockMvc
                .perform(
                    post("/news/sentiment/market/refresh")
                        .with(jwt().jwt(userOnlyToken()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `POST market refresh returns the rollup counts for an admin token`() {
            whenever(marketSentimentService.refresh()).thenReturn(MarketSentimentRefreshResult(scopes = 17, rows = 6))

            mockMvc
                .perform(
                    post("/news/sentiment/market/refresh")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.scopes").value(17))
                .andExpect(jsonPath("$.data.rows").value(6))
        }

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