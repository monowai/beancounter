package com.beancounter.shell.google;

import com.beancounter.shell.ingest.AbstractIngester;
import com.beancounter.shell.ingest.IngestionRequest;
import com.beancounter.shell.ingest.TrnWriter;
import com.google.api.services.sheets.v4.Sheets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads the actual google sheet.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
public class SheetIngester extends AbstractIngester {

  private final Logger log = LoggerFactory.getLogger(SheetIngester.class);
  private GoogleTransport googleTransport;
  private Sheets service;
  private IngestionRequest ingestionRequest;

  @Autowired
  void setGoogleTransport(GoogleTransport googleTransport) {
    this.googleTransport = googleTransport;
  }

  @Override
  public void prepare(IngestionRequest ingestionRequest, TrnWriter trnWriter) {
    service = googleTransport.getSheets(googleTransport.getHttpTransport());
    this.ingestionRequest = ingestionRequest;
  }

  @Override
  public List<List<String>> getValues() {
    log.info("Processing {} {}", googleTransport.getRange(), ingestionRequest.getFile());
    List<List<String>> results = new ArrayList<>();
    List<List<Object>> sheetResults =  googleTransport.getValues(
        service,
        ingestionRequest.getFile(),
        googleTransport.getRange());

    for (List<Object> sheetResult : sheetResults) {
      results.add(toStrings(sheetResult));
    }

    return results;
  }

  private List<String> toStrings(List<Object> sheetResult) {
    List<String> result = new ArrayList<>();
    for (Object o : sheetResult) {
      result.add(o == null ? null : o.toString());
    }
    return result;
  }


}