package com.beancounter.shell.ingest;

import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.client.ingest.RowAdapter;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.services.TrnService;
import com.beancounter.client.sharesight.ShareSightFactory;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
  private ShareSightFactory shareSightFactory;

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

  @Autowired
  void setShareSightService(ShareSightFactory shareSightService) {
    this.shareSightFactory = shareSightService;
  }

  @Override
  public void write(TrustedTrnRequest trustedTrnRequest) {
    List<String> row = trustedTrnRequest.getRow();
    TrnAdapter adapter = shareSightFactory.adapter(row);
    Asset asset = adapter.resolveAsset(row);
    if (asset == null) {
      return;
    }
    trustedTrnRequest.setAsset(asset);

    this.portfolio = trustedTrnRequest.getPortfolio();
    TrnInput trnInput = rowAdapter.transform(trustedTrnRequest);

    if (trnInput != null) {
      trnInputs.add(trnInput);
    }

  }

  @Override
  public void flush() {
    log.info("Back filling FX rates...");
    int rows;
    if (trnInputs != null && !trnInputs.isEmpty()) {
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
    }
    if (trnInputs != null) {
      trnInputs.clear();
    }
    portfolio = null;
    log.info("Complete!");
  }

  @Override
  public String id() {
    return "HTTP";
  }
}
