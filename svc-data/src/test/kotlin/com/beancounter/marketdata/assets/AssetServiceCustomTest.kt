package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.providers.MarketDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
internal class AssetServiceCustomTest {
    @Autowired
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var mockRepos: MockRepos

    @BeforeEach
    fun mockRepos() {
        mockRepos.currencies()
    }

    @Test
    fun is_RealEstateAsset() {
        val customAsset = AssetInput(market = "Custom", code = "House", category = "RE")
        val updateResponse = assetService.handle(AssetRequest(customAsset))
        assertThat(updateResponse.data[customAsset.code])
            .isNotNull
            .hasFieldOrPropertyWithValue("assetCategory.id", "RE")
            .hasFieldOrPropertyWithValue("assetCategory.name", "Real Estate")
            .hasFieldOrPropertyWithValue("market.code", "CUSTOM")
    }
}
