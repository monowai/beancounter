package com.beancounter.client;

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

@Slf4j
@Service
public class StaticService {
  public StaticGateway staticGateway;
  private ExchangeService exchangeService;

  private Map<String, Market> marketMap = new HashMap<>();

  StaticService(StaticGateway staticGateway, ExchangeService exchangeService) {
    this.staticGateway = staticGateway;
    this.exchangeService = exchangeService;
  }

  @Retry(name = "data")
  public MarketResponse getMarkets() {
    return staticGateway.getMarkets();
  }

  public CurrencyResponse getCurrencies() {
    return staticGateway.getCurrencies();
  }

  public Currency getCurrency(String currency) {
    if (currency == null) {
      return null;
    }
    return getCurrencies().getData().get(currency);
  }

  public Market get(String key) {
    // ToDo: getMarket by Code and add @Cache
    if (marketMap.isEmpty()) {
      Collection<Market> markets = staticGateway.getMarkets().getData();
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
    MarketResponse getMarkets();

    @GetMapping(value = "/currencies", produces = {MediaType.APPLICATION_JSON_VALUE})
    CurrencyResponse getCurrencies();

  }

}
