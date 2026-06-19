package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.INDEX_MARKET_CODE
import com.beancounter.marketdata.assets.IndexConfig
import com.beancounter.marketdata.assets.IndexSeedRunner
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.ALPHA_MOCK
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.mockHistoricResponse
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate

@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db", "alpha")
@Tag("wiremock")
@AutoConfigureMockAuth
@TestPropertySource(
    properties = ["beancounter.market.providers.alpha.markets=NASDAQ,AMEX,NYSE,ASX,LON,INDEX"]
)
class AlphaIndexApiTest {
    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension =
            WireMockExtension
                .newInstance()
                .options(WireMockConfiguration.options().dynamicPort())
                .configureStaticDsl(true)
                .build()
    }

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var priceService: PriceService

    @Autowired
    private lateinit var indexConfig: IndexConfig

    @Autowired
    private lateinit var indexSeedRunner: IndexSeedRunner

    @Test
    fun `pre-seeded indices are persisted as INDEX category assets`() {
        indexSeedRunner.seed()

        val codes = indexConfig.values.map { it.code }
        assertThat(codes).contains("GSPC")

        val response =
            assetService.handle(
                AssetRequest(
                    mapOf(
                        "gspc" to
                            AssetInput(
                                market = INDEX_MARKET_CODE,
                                code = "GSPC"
                            )
                    )
                )
            )
        val gspc = response.data["gspc"]
        assertThat(gspc).isNotNull
        assertThat(gspc!!.assetCategory.id).isEqualTo(AssetCategory.INDEX)
        assertThat(gspc.marketCode).isEqualTo(INDEX_MARKET_CODE)
    }

    @Test
    fun `backFill of index asset persists prices via TIME_SERIES_DAILY`() {
        mockHistoricResponse(
            "GSPC",
            ClassPathResource("$ALPHA_MOCK/gspc-historic.json").file
        )

        indexSeedRunner.seed()
        val gspc =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            "gspc" to
                                AssetInput(
                                    market = INDEX_MARKET_CODE,
                                    code = "GSPC"
                                )
                        )
                    )
                ).data["gspc"]!!

        marketDataService.backFill(gspc.id)

        val history =
            priceService.getPriceHistory(
                gspc.id,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
            )
        assertThat(history.prices)
            .describedAs("Index backFill should persist TIME_SERIES_DAILY prices")
            .hasSize(2)
        assertThat(history.prices.map { it.priceDate })
            .contains(LocalDate.of(2026, 5, 7), LocalDate.of(2026, 5, 6))
    }
}