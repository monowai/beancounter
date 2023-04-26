package com.beancounter.marketdata.assets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.currency.CurrencyRepository
import com.beancounter.marketdata.providers.MarketDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.Optional

@SpringBootTest
@AutoConfigureMockAuth
internal class AssetServiceCustomTest {
    @Autowired
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var marketDataService: MarketDataService

    @MockBean
    private lateinit var currencyRepository: CurrencyRepository

    @BeforeEach
    fun mockRepos() {
        Mockito.`when`(currencyRepository.findById(Constants.USD.code))
            .thenReturn(Optional.of(Constants.USD))
        Mockito.`when`(currencyRepository.findById(Constants.AUD.code))
            .thenReturn(Optional.of(Constants.AUD))
        Mockito.`when`(currencyRepository.findById(Constants.NZD.code))
            .thenReturn(Optional.of(Constants.NZD))
        Mockito.`when`(currencyRepository.findById(Constants.SGD.code))
            .thenReturn(Optional.of(Constants.SGD))
        Mockito.`when`(currencyRepository.findById(Constants.GBP.code))
            .thenReturn(Optional.of(Constants.GBP))
    }

    @Test
    fun is_RealEstateAsset() {
        val house = AssetInput(
            market = "Private",
            code = "House",
            category = AssetInput.realEstate
        )
        val updateResponse = assetService.handle(AssetRequest(house))
        assertThat(updateResponse.data[house.code])
            .isNotNull
            .hasFieldOrPropertyWithValue("assetCategory.id", AssetInput.realEstate)
            .hasFieldOrPropertyWithValue("assetCategory.name", "Real Estate")
            .hasFieldOrPropertyWithValue("market.code", "PRIVATE")
    }
}
