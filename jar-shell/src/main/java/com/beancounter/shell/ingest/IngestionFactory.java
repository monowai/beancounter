package com.beancounter.shell.ingest;

import com.beancounter.shell.csv.CsvIngester;
import com.beancounter.shell.google.GoogleConfig;
import com.beancounter.shell.google.SheetIngester;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
public class IngestionFactory {
  private Map<String, Ingester> ingesterMap = new HashMap<>();

  @Autowired
  void setCsvIngester(CsvIngester csvIngester) {
    add("CSV", csvIngester);
  }

  @Autowired(required = false)
  @ConditionalOnBean(GoogleConfig.class)
  void setSheetIngester(SheetIngester sheetIngester) {
    add("GSHEET", sheetIngester);
  }


  public Ingester getIngester(IngestionRequest ingestionRequest) {
    return ingesterMap.get(ingestionRequest.getType());
  }

  public void add(String key, Ingester ingester) {
    ingesterMap.put(key.toUpperCase(), ingester);
  }
}
