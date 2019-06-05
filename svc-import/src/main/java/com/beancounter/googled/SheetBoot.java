package com.beancounter.googled;

import com.beancounter.googled.reader.SheetReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Read a google sheet and create an output file.
 * @author mikeh
 * @since 2019-02-08
 */
@SpringBootApplication(scanBasePackages = "com.beancounter")
public class SheetBoot {

  public static void main(String[] args) {
    SpringApplication.run(SheetBoot.class, args);
  }

  @Autowired
  void setSheetReader(SheetReader sheetReader) throws IOException, GeneralSecurityException {
    sheetReader.doIt();
  }

}
