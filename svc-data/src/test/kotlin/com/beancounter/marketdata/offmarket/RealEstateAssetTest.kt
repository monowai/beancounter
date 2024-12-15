package com.beancounter.marketdata.offmarket

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetCategoryConfig
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.trn.cash.CashBalancesBean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * Simulate the flow of an off market real estate purchase.
 */
@SpringMvcDbTest
class RealEstateAssetTest {
    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var assetCategoryConfig: AssetCategoryConfig

    @Autowired
    private lateinit var priceService: PriceService

    @MockitoBean
    private lateinit var cashBalancesBean: CashBalancesBean

    @Autowired
    private lateinit var systemUserService: Registration

    val reInput =
        AssetInput.toRealEstate(
            currency = NZD,
            code = "HAKL",
            name = "My House In Auckland",
            owner = "test-user"
        )

    private val dateUtils = DateUtils()

    @Test
    fun isOffMarketAssetCreated() {
        val sysUser = SystemUser(id = "test-user")
        val token =
            mockAuthConfig.login(
                sysUser,
                this.systemUserService
            )
        assertThat(token)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "subject",
                sysUser.id
            )

        val category = assetCategoryConfig.get(reInput.category.uppercase())
        val assetResponse =
            assetService.handle(
                AssetRequest(
                    mapOf(
                        Pair(
                            NZD.code,
                            reInput
                        )
                    )
                )
            )
        assertThat(assetResponse.data).hasSize(1)
        val reAsset = assetResponse.data[NZD.code]
        assertThat(reAsset)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "assetCategory",
                category
            ).hasFieldOrPropertyWithValue(
                "name",
                reInput.name
            ).hasFieldOrPropertyWithValue(
                "priceSymbol",
                NZD.code
            ).hasFieldOrPropertyWithValue(
                "systemUser",
                sysUser
            )

        // Make sure we don't create the same asset for the same user twice
        assertThat(assetService.findOrCreate(reInput))
            .isEqualTo(reAsset)

        // Simple find
        assertThat(assetService.findLocally(reInput))
            .isEqualTo(reAsset)

        // Price Flow
        val marketData =
            priceService.getMarketData(
                reAsset!!,
                dateUtils.date
            )
        assertThat(marketData.isPresent).isFalse() // no price exists
        val housePrice = BigDecimal("10000.99")
        val priceResponse =
            marketDataService.getPriceResponse(
                PriceRequest(
                    dateUtils.today(),
                    listOf(PriceAsset(reAsset)),
                    closePrice = housePrice
                )
            )

        assertThat(priceResponse.data).isNotNull()
        assertThat(priceResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue(
                "close",
                housePrice
            )
    }
}