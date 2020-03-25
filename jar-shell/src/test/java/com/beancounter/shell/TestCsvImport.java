package com.beancounter.shell;


import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.csv.CsvIngester;
import com.beancounter.shell.ingest.HttpWriter;
import com.beancounter.shell.ingest.IngestionRequest;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class TestCsvImport {

  @Test
  @SneakyThrows
  void importCsv() {
    CsvIngester csvIngester = new CsvIngester();
    Portfolio portfolio = PortfolioUtils.getPortfolio("TEST");
    portfolio.setId("test");
    csvIngester.prepare(
        IngestionRequest.builder()
            .portfolio(portfolio)
            .file("/trades.csv").build(),
        new HttpWriter());
    List<List<String>> results = csvIngester.getValues();
    assertThat(results).isNotEmpty().hasSize(5);
  }
}
