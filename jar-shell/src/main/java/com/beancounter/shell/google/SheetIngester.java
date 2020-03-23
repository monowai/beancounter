package com.beancounter.shell.google;

import com.beancounter.shell.ingest.AbstractIngester;
import com.beancounter.shell.ingest.IngestionRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads the actual google sheet.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
@Slf4j
public class SheetIngester extends AbstractIngester {

  private GoogleTransport googleTransport;
  private Sheets service;
  private NetHttpTransport httpTransport;
  private IngestionRequest ingestionRequest;

  @Autowired
  void setGoogleTransport(GoogleTransport googleTransport) {
    this.googleTransport = googleTransport;
  }

  @Override
  protected void prepare(IngestionRequest ingestionRequest) {
    httpTransport = googleTransport.getHttpTransport();
    service = googleTransport.getSheets(httpTransport);
    this.ingestionRequest = ingestionRequest;
  }

  @Override
  protected List<List<Object>> getValues() {
    log.info("Processing {} {}", googleTransport.getRange(), ingestionRequest.getFile());
    return googleTransport.getValues(
        service,
        ingestionRequest.getFile(),
        googleTransport.getRange());
  }


}