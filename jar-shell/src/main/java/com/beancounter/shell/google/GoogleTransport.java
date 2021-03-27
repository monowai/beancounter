package com.beancounter.shell.google;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.exception.SystemException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Data
public class GoogleTransport {
  private final Logger log = LoggerFactory.getLogger(GoogleTransport.class);
  private GoogleAuthConfig googleAuthConfig;

  @Value("${range:All Trades Report}")
  private String range;


  GoogleTransport(GoogleAuthConfig googleAuthConfig) {
    this.googleAuthConfig = googleAuthConfig;
  }

  public NetHttpTransport getHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new SystemException(e.getMessage());
    }
  }

  Sheets getSheets(NetHttpTransport httpTransport) {
    return new Sheets.Builder(httpTransport, JacksonFactory.getDefaultInstance(),
        googleAuthConfig.getCredentials(httpTransport))
        .setApplicationName("BeanCounter")
        .build();
  }

  List<List<Object>> getValues(Sheets service, String sheetId, String range) {
    ValueRange response;
    try {
      response = service.spreadsheets()
          .values()
          .get(sheetId, range)
          .execute();
    } catch (IOException e) {
      throw new SystemException(e.getMessage());
    }
    List<List<Object>> values = response.getValues();
    if (values == null || values.isEmpty()) {
      log.error("No data found.");
      throw new BusinessException(String.format("No data found for %s %s", sheetId, range));
    }

    return values;
  }


}
