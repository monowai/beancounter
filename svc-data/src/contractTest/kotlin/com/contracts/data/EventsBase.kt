package com.contracts.data

import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.alpha.AlphaEventService
import com.beancounter.marketdata.providers.alpha.AlphaGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Event Contract Tests. Called by Spring Cloud Contract Verifier
 */
class EventsBase : ContractVerifierBase() {
    @MockitoBean
    private lateinit var assetService: AssetService

    @MockitoBean
    private lateinit var alphaGateway: AlphaGateway

    @Autowired
    private lateinit var alphaEventService: AlphaEventService

    private val asset =
        getTestAsset(
            code = "NDAQ",
            market = NASDAQ
        )

    @BeforeEach
    fun doIt() {
        Mockito
            .`when`(assetService.find(asset.id))
            .thenReturn(asset)
        Mockito
            .`when`(
                alphaGateway.getAdjusted(
                    asset.id,
                    "demo"
                )
            ).thenReturn(
                objectMapper.writeValueAsString(
                    objectMapper.readTree(
                        ClassPathResource("alpha/ndaq-events-full.json").file
                    )
                )
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