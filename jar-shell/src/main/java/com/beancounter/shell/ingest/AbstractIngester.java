package com.beancounter.shell.ingest;

import com.beancounter.client.FxTransactions;
import com.beancounter.client.PortfolioService;
import com.beancounter.client.TrnService;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Portfolio;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public abstract class AbstractIngester implements Ingester {
  private FxTransactions fxTransactions;
  private RowAdapter rowAdapter;
  private PortfolioService portfolioService;
  private TrnService trnService;

  @Autowired
  public void setRowAdapter(RowAdapter rowAdapter) {
    this.rowAdapter = rowAdapter;
  }

  @Autowired
  public void setPortfolioService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  @Autowired
  void setTrnService(TrnService trnService) {
    this.trnService = trnService;
  }

  @Autowired
  void setFxTransactions(FxTransactions fxTransactions) {
    this.fxTransactions = fxTransactions;
  }

  /**
   * Default ingestion flow.
   *
   * @param ingestionRequest parameters to run the import.
   * @return JSON transformation
   */
  @SneakyThrows
  public Collection<TrnInput> ingest(IngestionRequest ingestionRequest) {
    // Build a new authorized API client service.

    Portfolio portfolio = portfolioService.getPortfolioByCode(ingestionRequest.getPortfolioCode());
    if (portfolio == null) {
      throw new BusinessException(String.format("Unknown portfolio code %s. Have you created it?",
          ingestionRequest.getPortfolioCode()));
    }
    prepare(ingestionRequest);
    List<List<String>> values = getValues();

    Collection<TrnInput> trnInputs = rowAdapter.transform(
        portfolio,
        values,
        (ingestionRequest.getProvider() == null ? "SHEETS" : ingestionRequest.getProvider()));

    if (trnInputs.isEmpty()) {
      return new ArrayList<>();
    }
    log.info("Back filling FX rates...");
    trnInputs = fxTransactions.applyRates(portfolio, trnInputs);
    if (ingestionRequest.isTrnPersist()) {
      log.info("Writing {} transactions to portfolio {}",
          trnInputs.size(),
          portfolio.getCode());

      TrnRequest trnRequest = TrnRequest.builder()
          .portfolioId(portfolio.getId())
          .data(trnInputs)
          .build();
      TrnResponse response = trnService.write(trnRequest);
      log.info("Wrote {}", response.getData().size());
    }

    log.info("Complete!");
    return trnInputs;

  }

  public abstract void prepare(IngestionRequest ingestionRequest);

  public abstract List<List<String>> getValues();
}
