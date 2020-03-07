package com.beancounter.shell.integ;


import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.shell.cli.IngestionCommand;
import com.beancounter.shell.config.IngestionConfig;
import com.beancounter.shell.model.IngestionRequest;
import com.beancounter.shell.reader.Ingester;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(classes = {
    IngestionConfig.class
})
public class TestIngestCommand {
  @Autowired
  private IngestionCommand ingestionCommand;

  @MockBean
  private Ingester ingester;

  @Test
  void is_IngestionCommandRunning() {

    Mockito.when(ingester.ingest(IngestionRequest.builder()
        .filter(null)
        .portfolioCode("ABC")
        .sheetId("abc")
        .build())).thenReturn(new ArrayList<>());

    assertThat(ingestionCommand.ingest("abc", "ABC", null))
        .isEqualTo("Done");
  }

}
