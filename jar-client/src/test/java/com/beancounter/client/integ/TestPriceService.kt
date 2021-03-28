package com.beancounter.client.integ

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import java.util.ArrayList

@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ImportAutoConfiguration(
    ClientConfig::class
)
@SpringBootTest(classes = [ClientConfig::class])
class TestPriceService {
    @Autowired
    private lateinit var priceService: PriceService

    @Autowired
    private lateinit var assetIngestService: AssetIngestService

    @Test
    fun is_MarketDataFoundOnDate() {
        val asset = assetIngestService.resolveAsset("NASDAQ", "EBAY")
        val assets: MutableCollection<AssetInput> = ArrayList()
        assets.add(getAssetInput(asset))
        val priceRequest = PriceRequest("2019-10-18", assets)
        val response = priceService.getPrices(priceRequest)

        Assertions.assertThat(response).isNotNull
        Assertions.assertThat(response.data).isNotNull.hasSize(1)

        val marketData = response.data.iterator().next()
        Assertions.assertThat(marketData.asset.market).isNotNull
        Assertions.assertThat(marketData)
            .hasFieldOrProperty("close")
            .hasFieldOrProperty("open")
            .hasFieldOrProperty("high")
            .hasFieldOrProperty("low")
            .hasFieldOrProperty("priceDate")
    }
}
