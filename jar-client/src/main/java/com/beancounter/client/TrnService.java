package com.beancounter.client;

import com.beancounter.auth.TokenService;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.model.Portfolio;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public class TrnService {
  private TrnGateway trnGateway;
  private TokenService tokenService;

  TrnService(TrnGateway trnGateway, TokenService tokenService) {
    this.trnGateway = trnGateway;
    this.tokenService = tokenService;
  }

  public TrnResponse write(TrnRequest trnRequest) {
    return trnGateway.write(tokenService.getBearerToken(), trnRequest);
  }

  public TrnResponse read(Portfolio portfolio) {
    return trnGateway.read(tokenService.getBearerToken(), portfolio.getId());
  }

  @FeignClient(name = "trns",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface TrnGateway {
    @PostMapping(value = "/trns",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    TrnResponse write(
        @RequestHeader("Authorization") String bearerToken,
        TrnRequest trnRequest);

    @GetMapping(value = "/trns/{portfolioId}",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    TrnResponse read(
        @RequestHeader("Authorization") String bearerToken,
        @PathVariable("portfolioId") String portfolioId);


  }

}
