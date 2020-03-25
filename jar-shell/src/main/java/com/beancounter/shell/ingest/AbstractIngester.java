package com.beancounter.shell.ingest;

import com.beancounter.client.PortfolioService;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public abstract class AbstractIngester implements Ingester {
  private PortfolioService portfolioService;
  private TrnWriter trnWriter;

  @Autowired
  public void setPortfolioService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  @Autowired
  public void setTrnWriter(TrnWriter trnWriter) {
    this.trnWriter = trnWriter;
  }

  /**
   * Default ingestion flow.
   *
   * @param ingestionRequest parameters to run the import.
   */
  @SneakyThrows
  public void ingest(IngestionRequest ingestionRequest) {
    // Build a new authorized API client service.

    Portfolio portfolio = portfolioService.getPortfolioByCode(ingestionRequest.getPortfolioCode());
    if (portfolio == null) {
      throw new BusinessException(String.format("Unknown portfolio code %s. Have you created it?",
          ingestionRequest.getPortfolioCode()));
    }
    ingestionRequest.setPortfolio(portfolio);
    prepare(ingestionRequest, trnWriter);
    List<List<String>> rows = getValues();

    for (List<String> row : rows) {
      trnWriter.write(ingestionRequest, row);
    }

    trnWriter.flush(ingestionRequest);
  }

  public abstract void prepare(IngestionRequest ingestionRequest, TrnWriter trnWriter);

  public abstract List<List<String>> getValues();
}
