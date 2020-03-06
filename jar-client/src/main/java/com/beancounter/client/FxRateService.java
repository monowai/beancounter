package com.beancounter.client;

import com.beancounter.auth.TokenService;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
@Slf4j
public class FxRateService {

  private FxGateway fxGateway;
  private TokenService tokenService;

  @Autowired
  FxRateService(FxGateway bcGateway, TokenService tokenService) {
    this.fxGateway = bcGateway;
    this.tokenService = tokenService;
  }

  @Cacheable("fx-request")
  public FxResponse getRates(FxRequest fxRequest) {
    if (fxRequest.getPairs() == null || fxRequest.getPairs().isEmpty()) {
      return FxResponse.builder().data(new FxPairResults()).build();
    }
    return fxGateway.getRates(tokenService.getBearerToken(), fxRequest);
  }

  @FeignClient(name = "fxrates",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface FxGateway {
    @PostMapping(value = "/fx",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    FxResponse getRates(@RequestHeader("Authorization") String bearerToken,
                        FxRequest fxRequest);

  }

}
