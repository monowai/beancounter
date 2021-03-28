package com.beancounter.client.integ

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties

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

    @Test
    fun is_HydratedAssetFound() {
        val asset = assetIngestService.resolveAsset(
            "NASDAQ", "MSFT"
        )
        Assertions.assertThat(asset).isNotNull
        Assertions.assertThat(asset.id).isNotNull
        Assertions.assertThat(asset.market).isNotNull
        Assertions.assertThat(asset.market.currency).isNotNull
    }

    @Test
    fun is_MockAssetFound() {
        val asset = assetIngestService.resolveAsset("MOCK", "MSFT")
        Assertions.assertThat(asset).isNotNull
    }

    @Test
    fun is_NotFound() {
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException::class.java) {
            assetIngestService.resolveAsset("NASDAQ", "ABC")
        }
    }
}
