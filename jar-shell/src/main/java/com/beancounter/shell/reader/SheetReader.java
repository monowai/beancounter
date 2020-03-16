package com.beancounter.shell.reader;

import com.beancounter.client.PortfolioService;
import com.beancounter.client.TrnService;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.model.IngestionRequest;
import com.beancounter.shell.sharesight.ShareSightService;
import com.beancounter.shell.writer.FxTransactions;
import com.beancounter.shell.writer.IngestWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.SneakyThrows;
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
  private PortfolioService portfolioService;
  private TrnService trnService;

  private ObjectMapper objectMapper = new ObjectMapper();

  SheetReader(PortfolioService portfolioService, TrnService trnService) {
    this.portfolioService = portfolioService;
    this.trnService = trnService;
  }

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
  @SneakyThrows
  public Collection<TrnInput> ingest(IngestionRequest ingestionRequest) {
    // Build a new authorized API client service.

    Portfolio portfolio = portfolioService.getPortfolioByCode(ingestionRequest.getPortfolioCode());
    if (portfolio == null) {
      throw new BusinessException(String.format("Unknown portfolio code %s. Please create it.",
          ingestionRequest.getPortfolioCode()));
    }

    final NetHttpTransport httpTransport = googleTransport.getHttpTransport();

    Sheets service = googleTransport.getSheets(httpTransport);
    String sheetId = ingestionRequest.getSheetId();
    List<List<Object>> values = googleTransport.getValues(
        service,
        sheetId,
        shareSightService.getRange());

    try (OutputStream outputStream = ingestWriter.prepareFile(shareSightService.getOutFile())) {

      log.info("Processing {} {}", shareSightService.getRange(), sheetId);
      Collection<TrnInput> trnInputs = rowProcessor.transform(
          portfolio,
          values,
          new Filter(ingestionRequest.getFilter()),
          (ingestionRequest.getProvider() == null ? "SHEETS" : ingestionRequest.getProvider()));

      if (trnInputs.isEmpty()) {
        return new ArrayList<>();
      }
      log.info("Back filling FX rates...");
      trnInputs = fxTransactions.applyRates(portfolio, trnInputs);
      if (ingestionRequest.isTrnPersist()) {
        log.info("Received request to write {} transactions {}",
            trnInputs.size(),
            portfolio.getCode());

        TrnRequest trnRequest = TrnRequest.builder()
            .portfolioId(portfolio.getId())
            .data(trnInputs)
            .build();
        trnService.write(trnRequest);
      }

      if (outputStream != null) {
        log.info("Writing output...");
        outputStream.write(
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(trnInputs));

        log.info("Wrote {} transactions into file {}", trnInputs.size(),
            shareSightService.getOutFile());
      }
      log.info("Complete!");
      return trnInputs;

    }
  }


}