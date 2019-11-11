package com.beancounter.ingest.service;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.MarketResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;


@FeignClient(name = "marketData",
    url = "${marketdata.url:http://localhost:9510/api}")
@Configuration
public interface BcGateway {
  @GetMapping(value = "/currencies")
  CurrencyResponse getCurrencies();

  @PostMapping(value = "/fx", headers = {"Content-Type: application/json"})
  FxResponse getRates(FxRequest fxRequest);

  @GetMapping(value = "/markets")
  MarketResponse getMarkets();

}
