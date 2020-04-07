package com.beancounter.shell.ingest;

import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.client.services.TrnService;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Portfolio;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HttpWriter implements TrnWriter {

  Collection<TrnInput> trnInputs = new ArrayList<>();
  private Portfolio portfolio;
  private FxTransactions fxTransactions;
  private TrnService trnService;
  private RowAdapter rowAdapter;

  @Autowired
  void setFxTransactions(FxTransactions fxTransactions) {
    this.fxTransactions = fxTransactions;
  }

  @Autowired
  void setTrnService(TrnService trnService) {
    this.trnService = trnService;
  }

  @Autowired
  void setRowAdapter(RowAdapter rowAdapter) {
    this.rowAdapter = rowAdapter;
  }

  @Override
  public void reset() {
    this.portfolio = null;
    this.trnInputs = new ArrayList<>();
  }

  @Override
  public void write(TrustedTrnRequest trustedTrnRequest) {
    this.portfolio = trustedTrnRequest.getPortfolio();
    TrnInput trnInput = rowAdapter.transform(trustedTrnRequest);
    if (trnInput != null) {
      trnInputs.add(trnInput);
    }

  }

  @Override
  public void flush() {
    int rows;
    if (trnInputs != null && !trnInputs.isEmpty()) {
      log.info("Back filling FX rates...");
      rows = trnInputs.size();
      for (TrnInput trnInput : trnInputs) {
        fxTransactions.setTrnRates(portfolio, trnInput);
      }
      log.info("Writing {} transactions to portfolio {}",
          rows,
          portfolio.getCode());

      TrnRequest trnRequest = TrnRequest.builder()
          .portfolioId(portfolio.getId())
          .data(trnInputs)
          .build();

      TrnResponse response = trnService.write(trnRequest);
      if (response != null && response.getData() != null) {
        log.info("Wrote {}", response.getData().size());
      }
      log.info("Complete!");
    }
    reset();
  }

  @Override
  public String id() {
    return "HTTP";
  }
}
