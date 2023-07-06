package com.contracts.data

import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.alpha.AlphaEventService
import com.beancounter.marketdata.providers.alpha.AlphaGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource

/**
 * Event Contract Tests. Called by Spring Cloud Contract Verifier
 */
class EventsBase : ContractVerifierBase() {
    @MockBean
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var alphaGateway: AlphaGateway

    @Autowired
    private lateinit var alphaEventService: AlphaEventService

    private val asset = getTestAsset(code = "NDAQ", market = NASDAQ)

    @BeforeEach
    fun doIt() {
        Mockito.`when`(assetService.find(asset.id))
            .thenReturn(asset)
        Mockito.`when`(alphaGateway.getAdjusted(asset.id, "demo"))
            .thenReturn(
                BcJson().objectMapper.writeValueAsString(
                    BcJson().objectMapper.readTree(
                        ClassPathResource("alpha/ndaq-events-full.json").file,
                    ),
                ),
            )
    }

    @Test
    fun validateResults() {
        val results = alphaEventService.getEvents(asset)

        assertThat(results.data)
            .hasSize(10)

        for (marketData in results.data) {
            assertThat(isDividend(marketData) || isSplit(marketData))
        }
    }
}
