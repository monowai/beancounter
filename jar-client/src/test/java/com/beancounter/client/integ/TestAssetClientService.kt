package com.beancounter.client.integ

import com.beancounter.client.AssetService
import com.beancounter.client.config.ClientConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties

/**
 * Contract based asset tests.
 */
@AutoConfigureStubRunner(stubsMode = StubRunnerProperties.StubsMode.LOCAL, ids = ["org.beancounter:svc-data:+:stubs:10999"])
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
class TestAssetClientService {
    @Autowired
    private lateinit var assetService: AssetService

    @Test
    fun is_AssetByIdOk() {
        assertThat(assetService.find("KMI")).isNotNull
    }
}
