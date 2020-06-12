package com.beancounter.shell.ingest;

import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.input.TrustedTrnImportRequest;
import com.beancounter.common.model.Portfolio;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public abstract class AbstractIngester implements Ingester {
  private PortfolioServiceClient portfolioService;
  private final Map<String, TrnWriter> writers = new HashMap<>();

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
    if (portfolio == null) {
      throw new BusinessException(String.format("Unknown portfolio code %s. Have you created it?",
          ingestionRequest.getPortfolioCode()));
    }
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
      CallerRef callerRef = CallerRef.builder()
          .provider(ingestionRequest.getProvider() == null
              ? portfolio.getId() : ingestionRequest.getProvider())
          .batch(String.valueOf(i))
          .callerId(String.valueOf(i++))
          .build();

      TrustedTrnImportRequest trnRequest = TrustedTrnImportRequest.builder()
          .row(row)
          .portfolio(portfolio)
          .callerRef(CallerRef.from(callerRef, portfolio))
          .build();
      writer.write(trnRequest);
    }

    writer.flush();
  }

  public abstract void prepare(IngestionRequest ingestionRequest, TrnWriter trnWriter);

  public abstract List<List<String>> getValues();
}
