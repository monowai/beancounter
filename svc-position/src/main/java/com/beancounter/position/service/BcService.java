package com.beancounter.position.service;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.position.integration.BcGateway;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BcService {

  private BcGateway bcGateway;
  private Map<String, Currency> currencyMap = new HashMap<>();

  @Autowired
  BcService(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  @Async
  public CompletableFuture<PriceResponse> getMarketData(PriceRequest priceRequest) {
    return CompletableFuture.completedFuture(bcGateway.getPrices(priceRequest));
  }

  @Async
  public CompletableFuture<FxResponse> getFxData(FxRequest fxRequest) {
    return CompletableFuture.completedFuture(bcGateway.getRates(fxRequest));
  }

  public TrnResponse getTrn(Portfolio portfolio) {
    return bcGateway.read(portfolio.getId());
  }

  private Map<String, Currency> getCurrencies() {
    if (currencyMap.isEmpty()) {
      CurrencyResponse response = bcGateway.getCurrencies();
      if (response == null) {
        log.info("No currencies retrieved");
        return new HashMap<>();
      }
      currencyMap.putAll(response.getData());
    }
    return currencyMap;
  }


  public Currency getCurrency(Currency currency) {
    if (currency == null) {
      return null;
    }
    return getCurrencies().get(currency.getCode());
  }

  public MarketResponse getMarkets() {
    return bcGateway.getMarkets();
  }

  public Portfolio getPortfolioById(String portfolioId) {
    PortfolioRequest request = bcGateway.getPortfolioById(portfolioId);
    if (request == null || request.getData() == null) {
      throw new BusinessException(String.format("Unable to locate portfolio id [%s]", portfolioId));
    }
    return request.getData().iterator().next();
  }

  public Portfolio getPortfolioByCode(String portfolioCode) {
    PortfolioRequest request = bcGateway.getPortfolioByCode(portfolioCode.toUpperCase());
    if (request == null || request.getData() == null) {
      throw new BusinessException(
          String.format("Unable to locate portfolio code [%s]", portfolioCode.toUpperCase()));
    }
    return request.getData().iterator().next();

  }
}
