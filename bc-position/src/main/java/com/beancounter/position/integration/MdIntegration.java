package com.beancounter.position.integration;

import com.beancounter.common.model.MarketData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Obtains market data objects from the Market Data service. 
 * @author mikeh
 * @since 2019-02-01
 */
@Configuration
@FeignClient(name = "${org.beancounter.md.name}", url = "${org.beancounter.md.url}")
public interface MdIntegration {

  @CircuitBreaker(name = "marketdata")
  @RequestMapping(method = RequestMethod.GET, value = "/{assetId}")
  MarketData getMarketData(@PathVariable("assetId") String assetId);
}
