package com.beancounter.marketdata.providers.eodhd

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.marketdata.MarketDataBoot
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * WireMock coverage for [EodhdGateway.getSentiments]: the query shape EODHD expects, the body
 * passed through verbatim, and an empty body surfacing as an error rather than "no coverage".
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db", "eodhd")
@AutoConfigureMockAuth
internal class EodhdSentimentGatewayTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var gateway: EodhdGateway

    @AfterEach
    fun resetWireMock() = WireMock.reset()

    private fun stub(body: String) {
        wireMock.stubFor(
            get(urlPathEqualTo("/api/sentiments"))
                .withQueryParam("s", equalTo("AAPL.US,TSCO.LSE"))
                .withQueryParam("from", equalTo("2026-09-25"))
                .withQueryParam("fmt", equalTo("json"))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                        .withStatus(200)
                )
        )
    }

    @Test
    fun `returns the sentiment body verbatim for the requested symbols and window`() {
        val body = """{"AAPL.US":[{"date":"2026-10-06","count":27,"normalized":0.8937}]}"""
        stub(body)

        assertThat(gateway.getSentiments("AAPL.US,TSCO.LSE", "2026-09-25", "demo")).isEqualTo(body)
    }

    @Test
    fun `an empty body is an error naming the symbols rather than empty coverage`() {
        stub("")

        assertThatThrownBy { gateway.getSentiments("AAPL.US,TSCO.LSE", "2026-09-25", "demo") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("AAPL.US,TSCO.LSE")
    }

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension =
            WireMockExtension
                .newInstance()
                .options(WireMockConfiguration.options().dynamicPort())
                .configureStaticDsl(true)
                .build()

        @JvmStatic
        @DynamicPropertySource
        fun wireMockProps(registry: DynamicPropertyRegistry) {
            registry.add("wiremock.server.port") { wireMock.port }
        }
    }
}