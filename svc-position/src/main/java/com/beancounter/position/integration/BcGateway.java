package com.beancounter.position.integration;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Collection;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
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
@FeignClient(name = "${marketdata.name}", url = "${marketdata.url:http://localhost:9510/api}")
public interface BcGateway {

  @GetMapping(value = "/price/{assetId}")
  MarketData getMarketData(@PathVariable("assetId") String assetId);

  @GetMapping(value = "/price")
  PriceResponse getMarketData(Collection<Asset> assetId);

  @PostMapping(value = "/fx")
  FxResponse getRates(FxRequest fxRequest);

  @GetMapping(value = "/market")
  MarketResponse getMarkets();


}
