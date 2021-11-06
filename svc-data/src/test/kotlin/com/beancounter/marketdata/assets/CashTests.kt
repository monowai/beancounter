package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.Constants.Companion.NZD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Cash Asset tests.
 */
@SpringBootTest
class CashTests {
    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var assetCategoryConfig: AssetCategoryConfig

    @Test
    fun isCashAssetCreated() {
        val cashInput = AssetUtils.getCash(NZD.code)
        val category = assetCategoryConfig.get(cashInput.category.uppercase())
        val assetResponse = assetService.process(AssetRequest(mapOf(Pair("nz-cash", cashInput))))
        assertThat(assetResponse.data).hasSize(1)
        val cashAsset = assetResponse.data["nz-cash"]
        assertThat(cashAsset)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "assetCategory", category,
            ).hasFieldOrPropertyWithValue("name", cashInput.name)
            .hasFieldOrPropertyWithValue("priceSymbol", NZD.code)
    }
}
