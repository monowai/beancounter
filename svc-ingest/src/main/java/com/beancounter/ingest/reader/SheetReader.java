package com.beancounter.ingest.reader;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.identity.TransactionId;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.config.GoogleAuthConfig;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.beancounter.ingest.sharesight.common.ShareSightHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Lists;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SheetReader implements Ingester{

  private static final String APPLICATION_NAME = "BeanCounter ShareSight Reader";
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private GoogleAuthConfig googleAuthConfig;

  @Value("${sheet:#{null}}")
  private String sheetId;

  @Value("${filter:#{null}}")
  private String filter;

  private Collection<String> filteredAssets = new ArrayList<>();

  @Value(("${portfolio.code:mike}"))
  private String portfolioCode;

  @Value(("${portfolio.code:USD}"))
  private String portfolioCurrency;

  @Value(("${base.code:USD}"))
  private String baseCurrency;

  private ShareSightHelper shareSightHelper;

  private ShareSightTransformers shareSightTransformers;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  @VisibleForTesting
  void setGoogleAuthConfig(GoogleAuthConfig googleAuthConfig) {
    this.googleAuthConfig = googleAuthConfig;
  }

  @Autowired
  @VisibleForTesting
  void setShareSightHelper(ShareSightHelper shareSightHelper) {
    this.shareSightHelper = shareSightHelper;
  }

  @Autowired
  @VisibleForTesting
  void setShareSightTransformers(ShareSightTransformers shareSightTransformers) {
    this.shareSightTransformers = shareSightTransformers;
  }

  /**
   * Reads a Google sheet and writes the output file.
   *
   */
  public void ingest() {
    // Build a new authorized API client service.

    if (filter != null) {
      filteredAssets = Lists.newArrayList(Splitter.on(",").split(filter));
    }

    Portfolio portfolio = Portfolio.builder()
        .code(portfolioCode)
        .currency(Currency.builder().code(portfolioCurrency).build())
        .build();

    Currency systemBase = Currency.builder().code(baseCurrency).build();

    final NetHttpTransport httpTransport;
    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new SystemException(e.getMessage());
    }
    Sheets service;
    try {
      service = new Sheets.Builder(httpTransport, JSON_FACTORY,
          googleAuthConfig.getCredentials(httpTransport))
          .setApplicationName(APPLICATION_NAME)
          .build();
    } catch (IOException e) {
      throw new SystemException(e.getMessage());
    }

    ValueRange response;
    try {
      response = service.spreadsheets()
          .values()
          .get(sheetId, shareSightHelper.getRange())
          .execute();
    } catch (IOException e) {
      throw new SystemException(e.getMessage());
    }

    List<List<Object>> values = response.getValues();
    if (values == null || values.isEmpty()) {
      log.error("No data found.");
    } else {
      int trnId = 1;

      if (filter != null) {
        log.info("Filtering for assets matching {}", filter);
      }

      Collection<Transaction> transactions = new ArrayList<>();
      try (FileOutputStream outputStream = prepareFile()) {

        for (List row : values) {
          // Print columns in range, which correspond to indices 0 and 4.
          Transaction transaction;
          Transformer transformer = shareSightTransformers.transformer(row);

          try {
            if (transformer.isValid(row)) {
              transaction = transformer.from(row, portfolio, systemBase);

              if (transaction.getId() == null) {
                transaction.setId(TransactionId.builder()
                    .id(trnId++)
                    .batch(0)
                    .provider(sheetId)
                    .build());
              }
              if (addTransaction(transaction)) {
                transactions.add(transaction);
              }
            }
          } catch (ParseException e) {
            log.error("{} Parsing row {}", transformer.getClass().getSimpleName(), trnId);
            throw new SystemException(e.getMessage());
          }

        }
        if (!transactions.isEmpty()) {
          if (outputStream != null) {
            outputStream.write(
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(transactions));

            log.info("Wrote {} transactions into file {}", transactions.size(),
                shareSightHelper.getOutFile());
          } else {
            log.info(objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(transactions));
          }
        } else {
          log.info("No transactions were processed");
        }

      } catch (IOException e) {
        throw new SystemException(e.getMessage());
      }
    }
  }

  private boolean addTransaction(Transaction transaction) {
    if (filter != null) {
      return filteredAssets.contains(transaction.getAsset().getCode());
    }
    return true;
  }



  private FileOutputStream prepareFile() throws FileNotFoundException {
    if (shareSightHelper.getOutFile() == null) {
      return null;
    }
    return new FileOutputStream(shareSightHelper.getOutFile());
  }


}
