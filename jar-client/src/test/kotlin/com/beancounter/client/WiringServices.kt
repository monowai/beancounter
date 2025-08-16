package com.beancounter.client

import com.beancounter.auth.TokenService
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
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Simple wiring tests
 */
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:0.1.1:stubs:11000"]
)
class WiringServices {
    @Autowired
    private lateinit var assetIngestService: AssetIngestService

    @Autowired
    private lateinit var fxRateService: FxService

    @Autowired
    private lateinit var portfolioService: PortfolioServiceClient

    @Autowired
    private lateinit var priceService: PriceService

    @Autowired
    private lateinit var staticService: StaticService

    @Autowired
    private lateinit var trnService: TrnService

    @MockitoBean
    private lateinit var tokenService: TokenService

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