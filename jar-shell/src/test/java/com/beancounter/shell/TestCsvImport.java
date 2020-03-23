package com.beancounter.shell;


import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.shell.csv.CsvIngester;
import com.beancounter.shell.ingest.IngestionRequest;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class TestCsvImport {

  @Test
  @SneakyThrows
  void importCsv() {
    CsvIngester csvIngester = new CsvIngester();
    csvIngester.prepare(IngestionRequest.builder().file("/trades.csv").build());
    List<List<String>> results = csvIngester.getValues();
    assertThat(results).isNotEmpty().hasSize(5);
  }
}
