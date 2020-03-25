package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.shell.cli.IngestionCommand;
import com.beancounter.shell.config.IngestionConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

@Tag("slow")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ActiveProfiles("test")
@Slf4j
@SpringBootTest(classes = {IngestionConfig.class})
public class TestCsvImportFlow {

  @Autowired
  private IngestionCommand ingestionCommand;

  @Test
  void is_CsvCommandFlowWorking() {
    String result = ingestionCommand
        .ingest("CSV", "http", "/MSFT.csv", "TEST", null);
    assertThat(result).isEqualToIgnoringCase("DONE");
  }
}
