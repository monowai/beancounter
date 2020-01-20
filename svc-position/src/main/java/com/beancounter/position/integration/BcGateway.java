package com.beancounter.position.integration;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.model.MarketData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Obtains market data objects from the Market Data service.
 *
 * @author mikeh
 * @since 2019-02-01
 */
@Configuration
@CircuitBreaker(name = "marketdata")
@FeignClient(name = "marketdata", url = "${marketdata.url:http://localhost:9510/api}")
public interface BcGateway {

  @GetMapping(value = "/prices/{assetId}", produces = {MediaType.APPLICATION_JSON_VALUE})
  MarketData getPrices(@PathVariable("assetId") String assetId);

  @GetMapping(value = "/prices", produces = {MediaType.APPLICATION_JSON_VALUE})
  PriceResponse getPrices(PriceRequest priceRequest);

  @PostMapping(value = "/fx",
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE})
  FxResponse getRates(FxRequest fxRequest);

  @GetMapping(value = "/trns/{portfolioId}",
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE})
  TrnResponse read(@PathVariable("portfolioId") String portfolioId);

  @GetMapping(value = "/currencies", produces = {MediaType.APPLICATION_JSON_VALUE})
  CurrencyResponse getCurrencies();

  @GetMapping(value = "/markets", produces = {MediaType.APPLICATION_JSON_VALUE})
  MarketResponse getMarkets();

  @GetMapping(value = "/portfolios/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
  PortfolioRequest getPortfolioById(@PathVariable String id);

  @GetMapping(value = "/portfolios/{code}/code", produces = {MediaType.APPLICATION_JSON_VALUE})
  PortfolioRequest getPortfolioByCode(@PathVariable String code);

}
