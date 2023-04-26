package com.beancounter.marketdata.assets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.providers.MdFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@AutoConfigureMockAuth
class RealEstateAssetTest {
    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var mdFactory: MdFactory

    @Autowired
    private lateinit var assetCategoryConfig: AssetCategoryConfig

    val reInput = AssetInput.toRealEstate(
        currency = NZD,
        name = "My House In Auckland",
    )

    @Test
    fun is_AssetCreated() {
        val category = assetCategoryConfig.get(reInput.category.uppercase())
        val assetResponse = assetService.handle(AssetRequest(mapOf(Pair(NZD.code, reInput))))
        assertThat(assetResponse.data).hasSize(1)
        val cashAsset = assetResponse.data[NZD.code]
        assertThat(cashAsset)
            .isNotNull
            .hasFieldOrPropertyWithValue("assetCategory", category)
            .hasFieldOrPropertyWithValue("name", reInput.name)
            .hasFieldOrPropertyWithValue("priceSymbol", NZD.code)
    }
}