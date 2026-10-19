package com.beancounter.marketdata.assets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * A currency pair is an ordinary asset on the FX market, in the CURRENCY category and priced in
 * its quote currency, so the watchlist, prices and charts treat it like any other listing.
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db")
@AutoConfigureMockAuth
internal class FxPairAssetTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var marketService: MarketService

    @Test
    fun `should route the FX market to EODHD's FOREX exchange`() {
        val fx = marketService.getMarket("FX")

        assertThat(fx.getAlias("eodhd")).isEqualTo("FOREX")
    }

    @Test
    fun `should create a currency pair priced in its quote currency`() {
        val asset =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            "NZDSGD" to
                                AssetInput(
                                    market = "FX",
                                    code = "NZDSGD",
                                    name = "New Zealand Dollar/Singapore Dollar",
                                    currency = "SGD",
                                    category = "Currency"
                                )
                        )
                    )
                ).data
                .getValue("NZDSGD")

        assertThat(asset.market.code).isEqualTo("FX")
        assertThat(asset.assetCategory.id).isEqualTo("CURRENCY")
        assertThat(asset.accountingType?.currency?.code).isEqualTo("SGD")
    }
}