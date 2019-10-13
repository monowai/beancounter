package com.beancounter.ingest.service;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FxRateService {

  private BcGateway bcGateway;

  @Autowired
  @VisibleForTesting
  void setBcGateway(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  public FxResponse getRates(FxRequest fxRequest) {
    if (fxRequest.getPairs() == null || fxRequest.getPairs().isEmpty()) {
      return FxResponse.builder().data(new HashMap<>()).build();
    }
    return bcGateway.getRates(fxRequest);
  }

}
