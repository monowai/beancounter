package com.beancounter.ingest.reader;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.service.FxTransactions;
import com.beancounter.ingest.sharesight.ShareSightHelper;
import com.beancounter.ingest.writer.IngestWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.OutputStream;
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

  private IngestWriter ingestWriter;
  private FxTransactions fxTransactions;
  private ShareSightHelper shareSightHelper;
  private RowProcessor rowProcessor;

  private ObjectMapper objectMapper = new ObjectMapper();

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
  void setRowProcessor(RowProcessor rowProcessor) {
    this.rowProcessor = rowProcessor;
  }

  @Autowired
  @VisibleForTesting
  void setShareSightHelper(ShareSightHelper shareSightHelper) {
    this.shareSightHelper = shareSightHelper;
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

    final NetHttpTransport httpTransport = googleTransport.getHttpTransport();

    Sheets service = googleTransport.getSheets(httpTransport);

    List<List<Object>> values = googleTransport.getValues(
        service,
        sheetId,
        shareSightHelper.getRange());

    try (OutputStream outputStream = ingestWriter.prepareFile(shareSightHelper.getOutFile())) {

      log.info("Processing {} {}", shareSightHelper.getRange(), sheetId);
      Collection<Transaction> transactions = rowProcessor.process(
          portfolio,
          values,
          sheetId);

      if (transactions.isEmpty()) {
        return;
      }

      log.info("Back fill FX rates...");
      transactions = fxTransactions.applyRates(transactions);

      if (outputStream != null) {
        log.info("Writing output...");
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

    } catch (IOException e) {
      throw new SystemException(e.getMessage());
    }
  }


}
