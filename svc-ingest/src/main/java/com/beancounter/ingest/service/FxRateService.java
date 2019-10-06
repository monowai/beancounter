package com.beancounter.ingest.service;

import com.beancounter.common.model.FxResults;
import com.beancounter.common.request.FxRequest;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FxRateService {

  private FxGateway fxGateway;

  @Autowired
  @VisibleForTesting
  void setFxGateway(FxGateway fxGateway) {
    this.fxGateway = fxGateway;
  }

  public FxResults getRates(FxRequest fxRequest) {
    if (fxRequest.getPairs() == null || fxRequest.getPairs().isEmpty()) {
      return FxResults.builder().data(new HashMap<>()).build();
    }
    return fxGateway.getRates(fxRequest);
  }

}
