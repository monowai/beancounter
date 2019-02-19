package com.beancounter.googled.reader;

import com.beancounter.common.identity.TransactionId;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.googled.config.GoogleDocsConfig;
import com.beancounter.googled.format.Transformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


/**
 * Reads the actual google sheet.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
@Log4j2
public class SheetReader {

  private static final String APPLICATION_NAME = "BeanCounter ShareSight Reader";
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private GoogleDocsConfig googleDocsConfig;

  @Value("${sheet}")
  private String spreadsheetId;

  @Value("${com.beanconter.source.sheet.range:A4:L14}")
  private String range;

  @Value(("${com.beancounter.portfolio.code:mike}"))
  private String portfolio;

  private Transformer transformer;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  void setGoogleDocsConfig(GoogleDocsConfig googleDocsConfig) {
    this.googleDocsConfig = googleDocsConfig;
  }

  @Autowired
  void setTransformer(Transformer transformer) {
    this.transformer = transformer;
  }

  /**
   * Reads the sheet and writes the output file.
   *
   * @throws IOException              error
   * @throws GeneralSecurityException error
   */
  public void doIt() throws IOException, GeneralSecurityException {
    // Build a new authorized API client service.
    final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    Sheets service = new Sheets.Builder(httpTransport, JSON_FACTORY,
        googleDocsConfig.getCredentials(httpTransport))
        .setApplicationName(APPLICATION_NAME)
        .build();

    ValueRange response = service.spreadsheets()
        .values()
        .get(spreadsheetId, range)
        .execute();
    List<List<Object>> values = response.getValues();
    if (values == null || values.isEmpty()) {
      log.error("No data found.");
    } else {
      int trnId = 1;

      Collection<Transaction> transactions = new ArrayList<>();
      try (FileOutputStream outputStream = prepareFile()) {

        for (List row : values) {
          // Print columns in range, which correspond to indices 0 and 4.
          Transaction transaction;
          try {
            transaction = transformer.of(row);
          } catch (ParseException e) {
            log.error("Parsing row {}", trnId);
            throw new RuntimeException(e);
          }
          if (transaction.getPortfolio() == null) {
            transaction.setPortfolio(Portfolio.builder().code(portfolio).build());
          }

          if (transaction.getId() == null) {
            transaction.setId(TransactionId.builder()
                .id(trnId++)
                .batch(0)
                .provider(spreadsheetId)
                .build());
          }
          transactions.add(transaction);

        }
        if (!transactions.isEmpty()) {
          if (outputStream != null) {
            outputStream.write(
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(transactions));
            log.info("Wrote file to {}", transformer.getFileName());
          } else {
            log.info(objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(transactions));
          }


        }

      }
    }
  }

  private FileOutputStream prepareFile() throws FileNotFoundException {
    if (transformer.getFileName() == null) {
      return null;
    }
    return new FileOutputStream(transformer.getFileName());
  }


}