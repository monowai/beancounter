package com.beancounter.ingest.reader;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.identity.TransactionId;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.service.FxTransactions;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.beancounter.ingest.sharesight.common.ShareSightHelper;
import com.beancounter.ingest.writer.IngestWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.OutputStream;
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
public class SheetReader implements Ingester {

  private GoogleTransport googleTransport;

  @Value("${sheet:#{null}}")
  private String sheetId;

  @Value(("${portfolio.code:mike}"))
  private String portfolioCode;

  @Value(("${portfolio.currency:USD}"))
  private String portfolioCurrency;

  @Value(("${base.code:USD}"))
  private String baseCurrency;
  private Filter filter;
  private IngestWriter ingestWriter;
  private FxTransactions fxTransactions;
  private ShareSightHelper shareSightHelper;
  private ShareSightTransformers shareSightTransformers;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  @VisibleForTesting
  void setFilter(Filter filter) {
    this.filter = filter;
  }

  @Autowired
  @VisibleForTesting
  void setIngestWriter(IngestWriter ingestWriter) {
    this.ingestWriter = ingestWriter;
  }

  @Autowired
  @VisibleForTesting
  void setFxTransactions(FxTransactions fxTransactions) {
    this.fxTransactions = fxTransactions;
  }

  @Autowired
  @VisibleForTesting
  void setGoogleTransport(GoogleTransport googleTransport) {
    this.googleTransport = googleTransport;
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
   */
  public void ingest() {
    // Build a new authorized API client service.

    Portfolio portfolio = Portfolio.builder()
        .code(portfolioCode)
        .currency(Currency.builder().code(portfolioCurrency).build())
        .build();

    Currency systemBase = Currency.builder().code(baseCurrency).build();

    final NetHttpTransport httpTransport = googleTransport.getHttpTransport();

    Sheets service = googleTransport.getSheets(httpTransport);

    List<List<Object>> values = googleTransport.getValues(
        service,
        sheetId,
        shareSightHelper.getRange());

    int trnId = 1;

    if (filter != null) {
      log.info("Filtering for assets matching {}", filter);
    }

    Collection<Transaction> transactions = new ArrayList<>();
    try (OutputStream outputStream = ingestWriter.prepareFile(shareSightHelper.getOutFile())) {

      for (List row : values) {
        Transformer transformer = shareSightTransformers.transformer(row);

        try {
          if (transformer.isValid(row)) {
            Transaction transaction = transformer.from(row, portfolio, systemBase);

            if (transaction.getId() == null) {
              transaction.setId(TransactionId.builder()
                  .id(trnId++)
                  .batch(0)
                  .provider(sheetId)
                  .build());
            }
            if (filter.inFilter(transaction)) {
              transactions.add(transaction);
            }
          }
        } catch (ParseException e) {
          log.error("{} Parsing row {}", transformer.getClass().getSimpleName(), trnId);
          throw new SystemException(e.getMessage());
        }

      }
      if (!transactions.isEmpty()) {
        transactions = fxTransactions.applyRates(transactions);
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
