package com.beancounter.ingest;

import com.beancounter.ingest.reader.SheetReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;


/**
 * Read a google sheet and create an output file.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@SpringBootApplication(scanBasePackages = {"com.beancounter.ingest"})
public class IngestBoot {

  public static void main(String[] args) {
    SpringApplication.run(IngestBoot.class, args);

  }

  private SheetReader sheetReader;

  @Autowired
  private void setSheetReader(SheetReader sheetReader) {
    this.sheetReader = sheetReader;
  }

  @EventListener(ApplicationReadyEvent.class)
  void runIngestion () throws IOException, GeneralSecurityException {
    sheetReader.doIt();
  }

}
