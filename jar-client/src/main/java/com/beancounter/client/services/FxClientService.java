package com.beancounter.client.services;

import com.beancounter.auth.common.TokenService;
import com.beancounter.client.FxService;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public class FxClientService implements FxService {

  private final FxGateway fxGateway;
  private final TokenService tokenService;

  FxClientService(FxGateway bcGateway, TokenService tokenService) {
    this.fxGateway = bcGateway;
    this.tokenService = tokenService;
  }

  @Cacheable("fx-request")
  public FxResponse getRates(FxRequest fxRequest) {
    if (fxRequest.getPairs().isEmpty()) {
      return new FxResponse(new FxPairResults());
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
