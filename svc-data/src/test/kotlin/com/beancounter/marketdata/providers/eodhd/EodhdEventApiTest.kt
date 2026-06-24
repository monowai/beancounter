package com.beancounter.marketdata.providers.eodhd

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Market
import com.beancounter.marketdata.MarketDataBoot
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * End-to-end (Spring + WireMock) coverage for the EODHD events pipeline.
 *
 * The earlier [EodhdEventServiceTest] is a pure unit test that mocks the proxy; this complement
 * exercises [EodhdGateway.getDividends] / [EodhdGateway.getSplits] and the rate-limited
 * [EodhdProxy] wrappers through real HTTP — same WireMock pattern as [EodhdApiTest] uses for prices.
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db", "eodhd")
@AutoConfigureMockAuth
internal class EodhdEventApiTest {
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
        @org.springframework.test.context.DynamicPropertySource
        fun wireMockProps(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("wiremock.server.port") { wireMock.port }
        }
    }

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var eodhdEventService: EodhdEventService

    private val barc =
        Asset(
            id = "asset-barc",
            code = "BARC",
            market = Market("LON"),
            category = AssetCategory.INDEX // any category; doesn't gate behaviour
        )

    @Test
    fun `getEvents combines dividend and split rows from EODHD`() {
        stubArrayEndpoint("/api/div/BARC.LSE", "mock/eodhd/AAPL-US-div.json")
        stubArrayEndpoint("/api/splits/BARC.LSE", "mock/eodhd/AAPL-US-splits.json")

        val response = eodhdEventService.getEvents(barc)

        // 2 dividends + 1 split from the AAPL fixtures.
        assertThat(response.data).hasSize(3)
        val withDividend = response.data.filter { it.dividend.compareTo(BigDecimal.ZERO) != 0 }
        val withSplit = response.data.filter { it.split.compareTo(BigDecimal.ONE) != 0 }
        assertThat(withDividend).hasSize(2)
        assertThat(withSplit).hasSize(1)
        assertThat(withSplit.first().split).isEqualByComparingTo(BigDecimal("4"))
        assertThat(withSplit.first().priceDate).isEqualTo(LocalDate.of(2020, 8, 31))
    }

    @Test
    fun `getEvents emits empty dividend and split rows when endpoints return no data`() {
        // Distinct asset id so the @Cacheable("eodhd.asset.event") result from the previous
        // test doesn't leak across — caches survive within the Spring test context lifetime.
        val empty =
            Asset(
                id = "asset-empty",
                code = "EMPTY",
                market = Market("LON"),
                category = AssetCategory.INDEX
            )
        stubArrayEndpoint("/api/div/EMPTY.LSE", "mock/eodhd/no-data.json")
        stubArrayEndpoint("/api/splits/EMPTY.LSE", "mock/eodhd/no-data.json")

        val response = eodhdEventService.getEvents(empty)

        assertThat(response.data).isEmpty()
    }

    @Test
    fun `getEvents degrades to stored events when EODHD drops the connection`() {
        // bc-event consumes this on a RabbitMQ listener; a provider I/O error must not 500
        // (which exhausts the retry policy and drops the message — EVENT-1F). Degrade to the
        // repository instead. Distinct asset id keeps the @Cacheable result isolated.
        val flaky =
            Asset(
                id = "asset-flaky",
                code = "FLAKY",
                market = Market("LON"),
                category = AssetCategory.INDEX
            )
        wireMock.stubFor(
            get(urlPathEqualTo("/api/div/FLAKY.LSE"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        )

        val response = eodhdEventService.getEvents(flaky)

        // No stored events for this asset → empty, but crucially no exception propagated.
        assertThat(response.data).isEmpty()
    }

    @Test
    fun `does not cache the fallback so events return once the provider recovers`() {
        val recovering =
            Asset(
                id = "asset-recover",
                code = "RECOV",
                market = Market("LON"),
                category = AssetCategory.INDEX
            )
        // First call: provider drops the connection → empty fallback. Must NOT be cached.
        wireMock.stubFor(
            get(urlPathEqualTo("/api/div/RECOV.LSE"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        )
        assertThat(eodhdEventService.getEvents(recovering).data).isEmpty()

        // Provider recovers — a cached empty fallback would still answer empty here.
        wireMock.resetAll()
        stubArrayEndpoint("/api/div/RECOV.LSE", "mock/eodhd/AAPL-US-div.json")
        stubArrayEndpoint("/api/splits/RECOV.LSE", "mock/eodhd/AAPL-US-splits.json")

        assertThat(eodhdEventService.getEvents(recovering).data).hasSize(3)
    }

    private fun stubArrayEndpoint(
        path: String,
        fixturePath: String
    ) {
        val body = ClassPathResource(fixturePath).file.readText()
        wireMock.stubFor(
            get(urlPathEqualTo(path))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                        .withStatus(200)
                )
        )
    }
}