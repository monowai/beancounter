package com.beancounter.client.integ

import com.beancounter.auth.TokenService
import com.beancounter.client.Assets
import com.beancounter.client.config.ClientConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Contract based asset tests.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
class TestAssetClientService {
    @Autowired
    private lateinit var assets: Assets

    @MockitoBean
    private lateinit var tokenService: TokenService

    @Test
    fun is_AssetByIdOk() {
        assertThat(assets.find("KMI")).isNotNull
    }
}