package com.beancounter.ingest.service;

import com.beancounter.ingest.reader.SheetReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class IngestSheet {

  private SheetReader sheetReader;

  @Autowired
  private void setSheetReader(SheetReader sheetReader) {
    this.sheetReader = sheetReader;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runIngestion() throws IOException, GeneralSecurityException {
    sheetReader.doIt();
    System.exit(0);
  }

}
