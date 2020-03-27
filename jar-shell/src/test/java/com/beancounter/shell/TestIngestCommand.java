package com.beancounter.shell;


import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.services.PortfolioService;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.cli.IngestionCommand;
import com.beancounter.shell.config.IngestionConfig;
import com.beancounter.shell.ingest.AbstractIngester;
import com.beancounter.shell.ingest.IngestionFactory;
import com.beancounter.shell.ingest.IngestionRequest;
import com.beancounter.shell.ingest.TrnWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.stereotype.Service;

@SpringBootTest(classes = {
    IngestionConfig.class
})
public class TestIngestCommand {
  @Autowired
  private IngestionCommand ingestionCommand;

  @Autowired
  private IngestionFactory ingestionFactory;

  @MockBean
  private PortfolioService portfolioService;

  @MockBean
  private TrnWriter trnWriter;

  private MockIngester mockIngester = new MockIngester();

  @BeforeEach
  void mockServices() {
    Mockito.when(portfolioService.getPortfolioByCode("ABC"))
        .thenReturn(PortfolioUtils.getPortfolio("ABC"));
    mockIngester.setPortfolioService(portfolioService);
    Mockito.when(trnWriter.id()).thenReturn("mock");
    mockIngester.setTrnWriters(trnWriter);
  }

  @Test
  void is_IngestionCommandRunning() {
    ingestionFactory.add("MOCK", mockIngester);
    // Make sure we are not case sensitive when finding the ingestion approach to use.
    assertThat(ingestionCommand.ingest("mock", trnWriter.id(), "ABC", "ABC", null))
        .isEqualTo("Done");
  }

  @Service
  static class MockIngester extends AbstractIngester {

    @Override
    public void prepare(IngestionRequest ingestionRequest, TrnWriter trnWriter) {
      // Noop
    }

    @Override
    public List<List<String>> getValues() {
      return new ArrayList<>();
    }
  }

}
