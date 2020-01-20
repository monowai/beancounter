package com.beancounter.ingest.controller;

import com.beancounter.common.model.Trn;
import com.beancounter.ingest.model.IngestionRequest;
import com.beancounter.ingest.reader.SheetReader;
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
  void setSheetReader(SheetReader sheetReader) {
    this.sheetReader = sheetReader;
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  Collection<Trn> ingest(@RequestBody IngestionRequest ingestionRequest) {
    return sheetReader.ingest(ingestionRequest);
  }
}
