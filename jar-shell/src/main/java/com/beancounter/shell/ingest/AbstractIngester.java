package com.beancounter.shell.ingest;

import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.ImportFormat;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.beancounter.common.model.CallerRef;
import com.beancounter.common.model.Portfolio;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public abstract class AbstractIngester implements Ingester {
  private final Map<String, TrnWriter> writers = new HashMap<>();
  private PortfolioServiceClient portfolioService;

  @Autowired
  public void setPortfolioService(PortfolioServiceClient portfolioService) {
    this.portfolioService = portfolioService;
  }

  @Autowired
  public void setTrnWriters(TrnWriter... trnWriter) {
    for (TrnWriter writer : trnWriter) {
      String key = "DEFAULT";
      if (writer.id() != null) {
        key = writer.id().toUpperCase();
      }
      writers.put(key.toUpperCase(), writer);
    }
  }

  private TrnWriter getWriter(String id) {
    return writers.get(id.toUpperCase());
  }

  /**
   * Default ingestion flow.
   *
   * @param ingestionRequest parameters to run the import.
   */
  @SneakyThrows
  public void ingest(IngestionRequest ingestionRequest) {
    Portfolio portfolio = portfolioService.getPortfolioByCode(ingestionRequest.getPortfolioCode());
    TrnWriter writer = getWriter(ingestionRequest.getWriter());
    if (writer == null) {
      throw new BusinessException(
          String.format("Unable to resolve the Writer %s", ingestionRequest.getWriter())
      );
    }

    prepare(ingestionRequest, writer);
    List<List<String>> rows = getValues();
    int i = 0;
    for (List<String> row : rows) {
      CallerRef callerRef = new CallerRef(
          ingestionRequest.getProvider() == null
              ? portfolio.getId() : ingestionRequest.getProvider(),
          String.valueOf(i), String.valueOf(i++));

      TrustedTrnImportRequest trnRequest = new TrustedTrnImportRequest(
          portfolio, ImportFormat.SHARESIGHT, callerRef, "", row);
      writer.write(trnRequest);
    }

    writer.flush();
  }

  public abstract void prepare(IngestionRequest ingestionRequest, TrnWriter trnWriter);

  public abstract List<List<String>> getValues();
}
