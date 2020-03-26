package com.beancounter.client.services;

import com.beancounter.auth.client.TokenService;
import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Slf4j
@Service
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.exchanges")
public class StaticService {
  public StaticGateway staticGateway;
  private TokenService tokenService;

  @Getter
  @Setter
  private Map<String, String> aliases;
  private Map<String, Market> marketMap = new HashMap<>();


  StaticService(StaticGateway staticGateway,
                TokenService tokenService) {
    this.staticGateway = staticGateway;
    this.tokenService = tokenService;
  }

  /**
   * Return the Exchange code to use for the supplied input.
   *
   * @param input code that *might* have an alias.
   * @return the alias or input if no exception is defined.
   */
  public String resolveAlias(String input) {
    String alias = aliases.get(input);
    if (alias == null) {
      return input;
    } else {
      return alias;
    }

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

  private Market get(String key) {
    if (marketMap.isEmpty()) {
      Collection<Market> markets = staticGateway.getMarkets(tokenService.getBearerToken())
          .getData();
      for (Market market : markets) {
        marketMap.put(market.getCode(), market);
      }
    }
    return marketMap.get(key);
  }

  public Market getMarket(String marketCode) {
    if (marketCode == null) {
      throw new BusinessException("Null Market Code");
    }
    Market market = get(marketCode);
    if (market == null) {
      String errorMessage = String.format("Unable to resolve market code %s", marketCode);
      String byAlias = resolveAlias(marketCode);
      if (byAlias == null) {
        throw new BusinessException(errorMessage);
      }
      market = get(byAlias);
      if (market == null) {
        throw new BusinessException(errorMessage);
      }
    }
    return market;
  }

  @FeignClient(name = "static",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface StaticGateway {
    @GetMapping(value = "/markets", produces = {MediaType.APPLICATION_JSON_VALUE})
    MarketResponse getMarkets(@RequestHeader("Authorization") String bearerToken);

    @GetMapping(value = "/currencies", produces = {MediaType.APPLICATION_JSON_VALUE})
    CurrencyResponse getCurrencies(@RequestHeader("Authorization") String bearerToken);

  }

}
