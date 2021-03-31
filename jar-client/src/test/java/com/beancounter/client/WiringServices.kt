package com.beancounter.client

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.StaticService
import com.beancounter.client.services.TrnService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties

@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
/**
 * Simple wiring tests
 */
class WiringServices {
    @Autowired
    private val assetIngestService: AssetIngestService? = null

    @Autowired
    private val fxRateService: FxService? = null

    @Autowired
    private val portfolioService: PortfolioServiceClient? = null

    @Autowired
    private val priceService: PriceService? = null

    @Autowired
    private val staticService: StaticService? = null

    @Autowired
    private val trnService: TrnService? = null

    @Test
    fun is_Wired() {
        Assertions.assertThat(assetIngestService).isNotNull
        Assertions.assertThat(fxRateService).isNotNull
        Assertions.assertThat(portfolioService).isNotNull
        Assertions.assertThat(priceService).isNotNull
        Assertions.assertThat(staticService).isNotNull
        Assertions.assertThat(trnService).isNotNull
    }
}
