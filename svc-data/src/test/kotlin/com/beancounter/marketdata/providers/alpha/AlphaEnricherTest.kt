package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.utils.AlphaMockUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles

/**
 * Tests enricher behaviour.
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("alpha")
@Tag("slow")
@AutoConfigureWireMock(port = 0)
class AlphaEnricherTest {

    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var alphaEnricher: AlphaEnricher

    @Test
    fun is_DefaultAssetName() {
        val key = "ABC"
        AlphaMockUtils.mockSearchResponse(
            key,
            ClassPathResource(AlphaMockUtils.alphaContracts + "/global-empty.json").file
        )

        val assetRequest = AssetRequest(key, AssetInput(Constants.NASDAQ.code, key, "My Default Name"))
        val assetResponse = assetService.process(assetRequest)
        assertThat(assetResponse).isNotNull
        assertThat(assetResponse.data)
            .hasSize(1)
            .containsKey(assetRequest.data.iterator().next().key)

        val createdAsset = assetResponse.data.iterator().next().value
        assertThat(createdAsset)
            .hasFieldOrPropertyWithValue("name", createdAsset.name)
            .hasFieldOrPropertyWithValue("code", createdAsset.code)
    }

    @Test
    fun is_currencyMatching() {
        assertThat(alphaEnricher.currencyMatch("GBX", "GBP")).isTrue
        assertThat(alphaEnricher.currencyMatch("GBP", "GBP")).isTrue
        assertThat(alphaEnricher.currencyMatch("AUD", "GBP")).isFalse
    }
}
