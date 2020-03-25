package com.beancounter.shell.csv;

import com.beancounter.common.exception.SystemException;
import com.beancounter.shell.ingest.AbstractIngester;
import com.beancounter.shell.ingest.IngestionRequest;
import com.beancounter.shell.ingest.TrnWriter;
import com.opencsv.CSVReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CsvIngester extends AbstractIngester {

  private Reader reader;

  @Override
  public void prepare(IngestionRequest ingestionRequest, TrnWriter trnWriter) {
    String trimmedFile = ingestionRequest.getFile().trim();
    trnWriter.flush(ingestionRequest);
    try {
      // Unit tests
      File file = new ClassPathResource(trimmedFile).getFile();
      reader = Files.newBufferedReader(Paths.get(file.toURI()));
    } catch (IOException e) {
      try {
        // Runtime
        reader = Files.newBufferedReader(Paths.get(trimmedFile));
      } catch (IOException ex) {
        log.error(ex.getMessage());
      }
    }
    if (reader == null) {
      throw new SystemException(String.format("Unable to resolve %s", trimmedFile));
    }
    log.info("Import {}", trimmedFile);

  }

  @Override
  @SneakyThrows
  public List<List<String>> getValues() {
    List<List<String>> results = new ArrayList<>();
    try (CSVReader csvReader = new CSVReader(reader)) {
      csvReader.skip(1);// Skip header
      String[] line;
      while ((line = csvReader.readNext()) != null) {
        results.add(Arrays.asList(line));
      }
    } catch (IOException e) {
      throw new SystemException(e.getMessage());
    }
    return results;
  }
}
