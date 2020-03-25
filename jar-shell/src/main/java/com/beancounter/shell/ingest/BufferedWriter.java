package com.beancounter.shell.ingest;

import com.beancounter.client.FxTransactions;
import com.beancounter.client.TrnService;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.TrnInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BufferedWriter implements TrnWriter {

  private FxTransactions fxTransactions;
  private TrnService trnService;
  private RowAdapter rowAdapter;

  Collection<TrnInput> trnInputs = new ArrayList<>();

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
  public void write(IngestionRequest ingestionRequest, List<String> row) {
    TrnInput trnInput = rowAdapter.transform(
        ingestionRequest.getPortfolio(),
        row,
        (ingestionRequest.getProvider() == null ? "SHEETS" : ingestionRequest.getProvider()));

    if (trnInput != null) {
      trnInputs.add(trnInput);
    }

  }

  @Override
  public Collection<TrnInput> flush(IngestionRequest ingestionRequest) {
    if (trnInputs.isEmpty()) {
      return new ArrayList<>();
    }
    log.info("Back filling FX rates...");
    trnInputs = fxTransactions.applyRates(ingestionRequest.getPortfolio(), trnInputs);
    log.info("Writing {} transactions to portfolio {}",
        trnInputs.size(),
        ingestionRequest.getPortfolio().getCode());

    TrnRequest trnRequest = TrnRequest.builder()
        .portfolioId(ingestionRequest.getPortfolio().getId())
        .data(trnInputs)
        .build();
    TrnResponse response = trnService.write(trnRequest);
    trnInputs.clear();
    log.info("Wrote {}", response.getData().size());

    log.info("Complete!");
    return trnInputs;
  }
}
