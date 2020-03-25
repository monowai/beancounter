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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Slf4j
@Service
public class StaticService {
  public StaticGateway staticGateway;
  private ExchangeService exchangeService;
  private TokenService tokenService;

  private Map<String, Market> marketMap = new HashMap<>();
  private Map<String, Map<String, Market>> providerAliases = new HashMap<>();

  StaticService(StaticGateway staticGateway,
                ExchangeService exchangeService,
                TokenService tokenService) {
    this.staticGateway = staticGateway;
    this.exchangeService = exchangeService;
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

  private Market get(String key) {
    // ToDo: getMarket by Code and add @Cache
    if (marketMap.isEmpty()) {
      Collection<Market> markets = staticGateway.getMarkets(tokenService.getBearerToken())
          .getData();
      for (Market market : markets) {
        marketMap.put(market.getCode(), market);
      }
    }
    return marketMap.get(key);
  }

  public Market resolveMarket(String marketCode) {
    if (marketCode == null) {
      throw new BusinessException("Null Market Code");
    }
    Market market = get(exchangeService.resolveAlias(marketCode.toUpperCase()));
    if (market == null) {
      throw new BusinessException(String.format("Unable to resolve market code %s", marketCode));
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
