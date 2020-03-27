package com.beancounter.client.services;

import com.beancounter.auth.client.TokenService;
import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Slf4j
@Service
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.exchanges")
public class StaticService {
  public StaticGateway staticGateway;
  private TokenService tokenService;

  StaticService(StaticGateway staticGateway,
                TokenService tokenService) {
    this.staticGateway = staticGateway;
    this.tokenService = tokenService;
  }

  @Retry(name = "data")
  public MarketResponse getMarkets() {
    return staticGateway.getMarkets(tokenService.getBearerToken());
  }

  public CurrencyResponse getCurrencies() {
    return staticGateway.getCurrencies(tokenService.getBearerToken());
  }

  public Currency getCurrency(String currencyCode) {
    if (currencyCode == null) {
      return null;
    }
    Collection<Currency> currencies = getCurrencies().getData();
    for (Currency currency : currencies) {
      if (currency.getCode().equalsIgnoreCase(currencyCode)) {
        return currency;
      }
    }
    throw new BusinessException(String.format("Unable to resolve the currency %s", currencyCode));
  }

  @Cacheable("market")
  public Market getMarket(String marketCode) {
    if (marketCode == null) {
      throw new BusinessException("Null Market Code");
    }
    try {
      MarketResponse marketResponse =
          staticGateway.getMarketByCode(tokenService.getBearerToken(), marketCode.toUpperCase());
      return marketResponse.getData().iterator().next();
    } catch (RuntimeException re) {
      throw new BusinessException("Unable to resolve market code " + marketCode);
    }

  }

  @FeignClient(name = "static",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface StaticGateway {
    @GetMapping(value = "/markets", produces = {MediaType.APPLICATION_JSON_VALUE})
    MarketResponse getMarkets(
        @RequestHeader("Authorization") String bearerToken);

    @GetMapping(value = "/markets/{code}", produces = {MediaType.APPLICATION_JSON_VALUE})
    MarketResponse getMarketByCode(
        @RequestHeader("Authorization") String bearerToken, @PathVariable String code);

    @GetMapping(value = "/currencies", produces = {MediaType.APPLICATION_JSON_VALUE})
    CurrencyResponse getCurrencies(
        @RequestHeader("Authorization") String bearerToken);

  }

}
