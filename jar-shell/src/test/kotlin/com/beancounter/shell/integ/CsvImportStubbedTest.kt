package com.beancounter.shell.integ

import com.beancounter.auth.TokenService
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.shell.commands.IngestionCommand
import com.beancounter.shell.config.IngestionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * CSV Import integration test.
 */
@Tag("slow")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ActiveProfiles("test")
@SpringBootTest(
    classes = [IngestionCommand::class, ShareSightConfig::class, ClientConfig::class, IngestionConfig::class]
)
class CsvImportStubbedTest {
    @Autowired
    private lateinit var ingestionCommand: IngestionCommand

    @MockitoBean
    private lateinit var tokenService: TokenService

    @BeforeEach
    fun stubToken() {
        Mockito.`when`(tokenService.bearerToken).thenReturn("token")
    }

    @Test
    fun is_CsvCommandFlowWorking() {
        val result =
            ingestionCommand
                .ingest(
                    reader = "CSV",
                    writer = "HTTP",
                    file = "/MSFT.csv",
                    portfolio = "TEST"
                )
        assertThat(result).isEqualToIgnoringCase("DONE")
    }
}