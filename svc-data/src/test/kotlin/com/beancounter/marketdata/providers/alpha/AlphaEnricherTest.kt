package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.core.io.ClassPathResource
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Tests enricher behaviour.
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db", "alpha")
@Tag("wiremock")
@AutoConfigureMockAuth
class AlphaEnricherTest {
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
    lateinit var assetService: AssetService

    @Autowired
    lateinit var alphaEnricher: AlphaEnricher

    @Test
    fun is_DefaultAssetName() {
        val key = "ABC"
        AlphaMockUtils.mockSearchResponse(
            key,
            ClassPathResource(AlphaMockUtils.ALPHA_MOCK + "/global-empty.json").file
        )

        val assetRequest =
            AssetRequest(
                AssetInput(
                    Constants.NASDAQ.code,
                    key,
                    "My Default Name"
                )
            )
        val assetResponse = assetService.handle(assetRequest)
        assertThat(assetResponse).isNotNull
        assertThat(assetResponse.data)
            .hasSize(1)
            .containsKey(
                assetRequest.data
                    .iterator()
                    .next()
                    .key
            )

        val createdAsset =
            assetResponse.data
                .iterator()
                .next()
                .value
        assertThat(createdAsset)
            .hasFieldOrPropertyWithValue(
                "name",
                createdAsset.name
            ).hasFieldOrPropertyWithValue(
                "code",
                createdAsset.code
            )
    }

    @Test
    fun is_currencyMatching() {
        assertThat(
            alphaEnricher.currencyMatch(
                "GBX",
                "GBP"
            )
        ).isTrue
        assertThat(
            alphaEnricher.currencyMatch(
                "GBP",
                "GBP"
            )
        ).isTrue
        assertThat(
            alphaEnricher.currencyMatch(
                "AUD",
                "GBP"
            )
        ).isFalse
    }
}