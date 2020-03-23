package com.beancounter.shell.csv;

import com.beancounter.shell.ingest.AbstractIngester;
import com.beancounter.shell.ingest.IngestionRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CsvIngester extends AbstractIngester {

  @Override
  protected void prepare(IngestionRequest ingestionRequest) {
  }

  @Override
  protected List<List<Object>> getValues() {
    return new ArrayList<>();
  }
}
