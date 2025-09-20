package com.beancounter.marketdata.cash

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.AssetCategoryConfig
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.MdFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Cash Asset tests.
 */
@SpringBootTest
@AutoConfigureMockAuth
class CashAssetTests {
    companion object {
        const val CASH = "CASH"
    }

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var mdFactory: MdFactory

    @Autowired
    private lateinit var assetCategoryConfig: AssetCategoryConfig

    @Test
    fun isCashAssetCreated() {
        val cashInput = AssetUtils.getCash(NZD.code)
        val category = assetCategoryConfig.get(cashInput.category.uppercase())
        val assetResponse =
            assetService.handle(
                AssetRequest(
                    mapOf(
                        Pair(
                            NZD.code,
                            cashInput
                        )
                    )
                )
            )
        assertThat(assetResponse.data).hasSize(1)
        val cashAsset = assetResponse.data[NZD.code]
        assertThat(cashAsset)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "assetCategory",
                category
            ).hasFieldOrPropertyWithValue(
                "name",
                cashInput.name
            ).hasFieldOrPropertyWithValue(
                "priceSymbol",
                NZD.code
            )
    }

    @Test
    fun is_UsdCashBalanceFound() {
        val found =
            assetService.findOrCreate(
                AssetInput(
                    CASH,
                    USD.code
                )
            )
        assertThat(found)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "assetCategory.id",
                CASH
            ).hasFieldOrPropertyWithValue(
                "name",
                "${USD.code} Balance"
            )
    }

    @Test
    fun is_MarketProviderFound() {
        assertThat(mdFactory.getMarketDataProvider(CASH)).isNotNull
    }
}