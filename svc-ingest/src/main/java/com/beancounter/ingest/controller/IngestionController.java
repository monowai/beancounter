package com.beancounter.ingest.controller;

import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.model.IngestionRequest;
import com.beancounter.ingest.reader.SheetReader;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/")
public class IngestionController {
  private SheetReader sheetReader;

  @Autowired
  @VisibleForTesting
  void setSheetReader(SheetReader sheetReader) {
    this.sheetReader = sheetReader;
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  Collection<Transaction> ingest(@RequestBody IngestionRequest ingestionRequest) {
    return sheetReader.ingest(ingestionRequest);
  }
}
