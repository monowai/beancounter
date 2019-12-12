package com.beancounter.ingest.reader;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.model.IngestionRequest;
import com.beancounter.ingest.service.FxTransactions;
import com.beancounter.ingest.sharesight.ShareSightService;
import com.beancounter.ingest.writer.IngestWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

  private IngestWriter ingestWriter;
  private FxTransactions fxTransactions;
  private ShareSightService shareSightService;
  private RowProcessor rowProcessor;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  void setIngestWriter(IngestWriter ingestWriter) {
    this.ingestWriter = ingestWriter;
  }

  @Autowired
  void setFxTransactions(FxTransactions fxTransactions) {
    this.fxTransactions = fxTransactions;
  }

  @Autowired
  void setGoogleTransport(GoogleTransport googleTransport) {
    this.googleTransport = googleTransport;
  }

  @Autowired
  void setRowProcessor(RowProcessor rowProcessor) {
    this.rowProcessor = rowProcessor;
  }

  @Autowired
  void setShareSightService(ShareSightService shareSightService) {
    this.shareSightService = shareSightService;
  }

  /**
   * Reads a Google sheet and writes the output file.
   *
   * @param ingestionRequest parameters to run the import.
   * @return JSON transformation
   */
  public Collection<Transaction> ingest(IngestionRequest ingestionRequest) {
    // Build a new authorized API client service.

    Portfolio portfolio = Portfolio.builder()
        .code(ingestionRequest.getPortfolio().getCode())
        .currency(ingestionRequest.getPortfolio().getCurrency())
        .base(ingestionRequest.getPortfolio().getBase())
        .build();

    final NetHttpTransport httpTransport = googleTransport.getHttpTransport();

    Sheets service = googleTransport.getSheets(httpTransport);
    String sheetId = ingestionRequest.getSheetId();
    List<List<Object>> values = googleTransport.getValues(
        service,
        sheetId,
        shareSightService.getRange());

    try (OutputStream outputStream = ingestWriter.prepareFile(shareSightService.getOutFile())) {

      log.info("Processing {} {}", shareSightService.getRange(), sheetId);
      Collection<Transaction> transactions = rowProcessor.process(
          portfolio,
          values,
          new Filter(ingestionRequest.getFilter()),
          sheetId);

      if (transactions.isEmpty()) {
        return new ArrayList<>();
      }

      log.info("Back filling FX rates...");
      transactions = fxTransactions.applyRates(transactions);

      if (outputStream != null) {
        log.info("Writing output...");
        outputStream.write(
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(transactions));

        log.info("Wrote {} transactions into file {}", transactions.size(),
            shareSightService.getOutFile());
      }
      log.info("Complete!");
      return transactions;

    } catch (IOException e) {
      throw new SystemException(e.getMessage());
    }
  }


}
