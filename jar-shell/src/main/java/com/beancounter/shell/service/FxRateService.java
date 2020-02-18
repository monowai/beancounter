package com.beancounter.shell.service;

import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PortfolioRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Service
@Slf4j
public class FxRateService {

  private FxGateway fxGateway;

  @Autowired
   FxRateService(FxGateway bcGateway) {
    this.fxGateway = bcGateway;
  }

  public FxResponse getRates(FxRequest fxRequest) {
    if (fxRequest.getPairs() == null || fxRequest.getPairs().isEmpty()) {
      return FxResponse.builder().data(new FxPairResults()).build();
    }
    return fxGateway.getRates(fxRequest);
  }

  @FeignClient(name = "fxrates",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface FxGateway {
    @PostMapping(value = "/fx",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    FxResponse getRates(FxRequest fxRequest);

  }

}
