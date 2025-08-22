package com.beancounter.client.integ

import com.beancounter.auth.TokenService
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test asset ingestion capabilities.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:0.1.1:stubs:10990"]
)
@ImportAutoConfiguration(
    ClientConfig::class
)
@SpringBootTest(classes = [ClientConfig::class])
@ActiveProfiles("jar-client-shared", "contract-base")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TestAssetIngestService {
    @Autowired
    private lateinit var assetIngestService: AssetIngestService

    @MockitoBean
    private lateinit var tokenService: TokenService

    private val msft = "MSFT"

    private val nasdaq = "NASDAQ"

    @Test
    fun `should find hydrated asset`() {
        val asset =
            assetIngestService.resolveAsset(
                AssetInput(
                    nasdaq,
                    msft
                )
            )
        assertThat(asset)
            .isNotNull
            .hasFieldOrProperty("id")
            .hasFieldOrProperty("market")
            .hasFieldOrProperty("market.currency")
    }

    @Test
    fun `should throw exception when asset not found`() {
        assertThrows(BusinessException::class.java) {
            assetIngestService.resolveAsset(
                AssetInput(
                    nasdaq,
                    "ABC"
                )
            )
        }
    }
}