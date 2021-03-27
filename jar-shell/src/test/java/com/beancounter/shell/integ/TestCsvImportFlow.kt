package com.beancounter.shell.integ

import com.beancounter.client.config.ClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.shell.cli.IngestionCommand
import com.beancounter.shell.config.IngestionConfig
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles

@Tag("slow")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ActiveProfiles("test")
@SpringBootTest(classes = [IngestionCommand::class, ShareSightConfig::class, ClientConfig::class, IngestionConfig::class])
class TestCsvImportFlow {
    @Autowired
    private lateinit var ingestionCommand: IngestionCommand
    @Test
    fun is_CsvCommandFlowWorking() {
        val result = ingestionCommand
            .ingest("CSV", "http", "/MSFT.csv", "TEST", null)
        Assertions.assertThat(result).isEqualToIgnoringCase("DONE")
    }
}
