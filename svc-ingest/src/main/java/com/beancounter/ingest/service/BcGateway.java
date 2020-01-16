package com.beancounter.ingest.service;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.MarketData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;


@FeignClient(name = "marketdata",
    url = "${marketdata.url:http://localhost:9510/api}")
@Configuration
public interface BcGateway {

  @GetMapping(value = "/prices/{assetId}", produces = {MediaType.APPLICATION_JSON_VALUE})
  MarketData getPrices(@PathVariable("assetId") String assetId);

  @GetMapping(value = "/prices", produces = {MediaType.APPLICATION_JSON_VALUE})
  PriceResponse getPrices(PriceRequest priceRequest);

  @PostMapping(value = "/fx",
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE})
  FxResponse getRates(FxRequest fxRequest);

  @PostMapping(value = "/assets",
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE})
  AssetResponse assets(AssetRequest assetRequest);

  @GetMapping(value = "/currencies", produces = {MediaType.APPLICATION_JSON_VALUE})
  CurrencyResponse getCurrencies();

  @GetMapping(value = "/markets", produces = {MediaType.APPLICATION_JSON_VALUE})
  MarketResponse getMarkets();

  @GetMapping(value = "/portfolios/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
  PortfolioRequest getPortfolioById(@PathVariable("id") String id);

  @GetMapping(value = "/portfolios/{code}/code", produces = {MediaType.APPLICATION_JSON_VALUE})
  PortfolioRequest getPortfolioByCode(@PathVariable("code") String code);

}
