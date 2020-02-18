package com.beancounter.shell.service;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Market;
import com.beancounter.shell.config.ExchangeConfig;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@Service
@Slf4j
public class StaticService {
  public StaticGateway staticGateway;
  private ExchangeConfig exchangeConfig;

  private Map<String, Market> marketMap = new HashMap<>();

  StaticService(StaticGateway staticGateway, ExchangeConfig exchangeConfig) {
    this.staticGateway = staticGateway;
    this.exchangeConfig = exchangeConfig;
  }

  @PostConstruct
  void initMarkets() {
    Collection<Market> markets = staticGateway.getMarkets().getData();
    for (Market market : markets) {
      marketMap.put(market.getCode(), market);
    }
    log.info("Connection to the data service is successful");

  }

  @Retry(name = "data")
  public MarketResponse getMarkets() {
    return staticGateway.getMarkets();
  }

  public CurrencyResponse getCurrencies() {
    return staticGateway.getCurrencies();
  }

  public Market get(String key) {
    return marketMap.get(key);
  }

  public Market resolveMarket(String marketCode,
                              AssetService assetService,
                              StaticService staticService) {
    if (marketCode == null) {
      throw new BusinessException("Null Market Code");
    }
    Market market = staticService.get(exchangeConfig.resolveAlias(marketCode.toUpperCase()));
    if (market == null) {
      throw new BusinessException(String.format("Unable to resolve market code %s", marketCode));
    }
    return market;
  }

  @FeignClient(name = "static",
      url = "${marketdata.url:http://localhost:9510/api}")
  interface StaticGateway {
    @GetMapping(value = "/markets", produces = {MediaType.APPLICATION_JSON_VALUE})
    MarketResponse getMarkets();

    @GetMapping(value = "/currencies", produces = {MediaType.APPLICATION_JSON_VALUE})
    CurrencyResponse getCurrencies();

  }

}
