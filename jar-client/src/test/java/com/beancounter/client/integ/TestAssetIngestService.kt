package com.beancounter.client.integ

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties

/**
 * Test asset ingestion capabilities.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ImportAutoConfiguration(
    ClientConfig::class
)
@SpringBootTest(classes = [ClientConfig::class])
class TestAssetIngestService {
    @Autowired
    private lateinit var assetIngestService: AssetIngestService

    private val msft = "MSFT"

    private val nasdaq = "NASDAQ"

    @Test
    fun is_HydratedAssetFound() {
        val asset = assetIngestService.resolveAsset(
            nasdaq, msft
        )
        assertThat(asset).isNotNull
        assertThat(asset.id).isNotNull
        assertThat(asset.market).isNotNull
        assertThat(asset.market.currency).isNotNull
    }

    @Test
    fun is_MockAssetFound() {
        val asset = assetIngestService.resolveAsset("MOCK", msft)
        assertThat(asset).isNotNull
    }

    @Test
    fun is_NotFound() {
        assertThrows(BusinessException::class.java) {
            assetIngestService.resolveAsset(nasdaq, "ABC")
        }
    }
}
