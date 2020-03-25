package com.beancounter.client.services;

import com.beancounter.auth.client.TokenService;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
@Slf4j
public class PriceService {

  private PriceGateway priceGateway;
  private TokenService tokenService;

  @Autowired
  PriceService(PriceGateway priceGateway, TokenService tokenService) {
    this.priceGateway = priceGateway;
    this.tokenService = tokenService;
  }

  public PriceResponse getPrices(PriceRequest priceRequest) {
    return priceGateway.getPrices(tokenService.getBearerToken(), priceRequest);
  }

  @FeignClient(name = "prices",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface PriceGateway {
    @GetMapping(
        value = "/prices",
        produces = {MediaType.APPLICATION_JSON_VALUE})
    PriceResponse getPrices(@RequestHeader("Authorization") String bearerToken,
                            PriceRequest priceRequest);

  }

}
