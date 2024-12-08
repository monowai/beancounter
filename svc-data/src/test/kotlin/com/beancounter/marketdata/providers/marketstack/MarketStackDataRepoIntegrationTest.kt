package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.MarketDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringMvcDbTest
@Transactional
class MarketStackDataRepoIntegrationTest {
    @Autowired
    lateinit var marketDataRepo: MarketDataRepo

    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var marketService: MarketService

    @Autowired
    lateinit var marketDataService: MarketDataService

    @Test
    fun `verify findByAssetInAndPriceDate returns Asset in MarketData`() {
        // Create an Asset
        val assetInput =
            AssetInput(
                market = NASDAQ.code,
                code = "TEST",
                name = "Test Asset",
            )
        val assetRequest = AssetRequest(mapOf("TEST" to assetInput))
        val assetResponse = assetService.handle(assetRequest)
        val asset = assetResponse.data.values.first()
        val priceDate =
            LocalDate.of(
                2024,
                11,
                15,
            )

        // Create MarketData
        val marketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal.TEN,
            )
        marketDataRepo.save(marketData)

        val result =
            marketDataRepo.findByAssetInAndPriceDate(
                listOf(asset),
                priceDate,
            )

        // Verify the Asset is returned in the MarketData object
        assertThat(result).isNotEmpty
        assertThat(result.first().asset).isEqualTo(asset)
        val mdPrice =
            marketDataService.getPriceResponse(
                PriceRequest(
                    date = priceDate.toString(),
                    assets = listOf(PriceAsset(asset)),
                ),
            )
        mdPrice.data.forEach {
            assertThat(it.asset).isEqualTo(asset)
        }
    }
}
