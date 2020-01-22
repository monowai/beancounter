package com.beancounter.shell.service;

import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FxRateService {

  private BcGateway bcGateway;

  @Autowired
  void setBcGateway(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  public FxResponse getRates(FxRequest fxRequest) {
    if (fxRequest.getPairs() == null || fxRequest.getPairs().isEmpty()) {
      return FxResponse.builder().data(new FxPairResults()).build();
    }
    return bcGateway.getRates(fxRequest);
  }

}
