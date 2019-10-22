package com.beancounter.position.service;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.accumulation.Gains;
import com.beancounter.position.accumulation.MarketValue;
import com.beancounter.position.model.FxReport;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import com.beancounter.position.model.ValuationData;
import com.beancounter.position.utils.FxUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;


/**
 * Integrations with MarketData providers to obtain prices
 * and value Positions.
 *
 * @author mikeh
 * @since 2019-02-24
 */
@Slf4j
@Configuration
@Service
public class ValuationService implements Valuation {

  private BcService bcService;
  private Gains gains = new Gains();
  private DateUtils dateUtils = new DateUtils();
  private MarketValue marketValue = new MarketValue();

  @Value("${marketdata.url}")
  private String mdUrl;

  @Autowired
  ValuationService(BcService bcService) {
    this.bcService = bcService;
  }

  @Override
  public Positions value(Positions positions) {
    if (positions.getAsAt() != null) {
      dateUtils.isValid(positions.getAsAt());
    }
    Collection<Asset> assets = new ArrayList<>();
    for (Position position : positions.getPositions().values()) {
      gains.value(position, Position.In.PORTFOLIO);
      if (!position.getQuantityValues().getTotal().equals(BigDecimal.ZERO)) {
        assets.add(position.getAsset());
      }
    }

    return value(positions, assets);
  }

  private Positions value(Positions positions, Collection<Asset> assets) {
    // Anything to value?
    if (assets.isEmpty()) {
      return positions;
    }
    log.debug("Valuing {} positions against : {}", positions.getPositions().size(), mdUrl);

    FxReport fxReport = FxReport.builder()
        .base(positions.getPortfolio().getBase())
        .portfolio(positions.getPortfolio().getCurrency())
        .build();
    // Set market data into the positions
    ValuationData valuationData = getValuationData(positions, assets);
    Map<CurrencyPair, FxRate> rates = valuationData.getFxResponse().getData().getRates();
    for (MarketData marketData : valuationData.getPriceResponse().getData()) {
      Position position = positions.get(marketData.getAsset());
      if (!marketData.getClose().equals(BigDecimal.ZERO)) {
        marketValue.value(position, fxReport, marketData, rates);
        position.setAsset(marketData.getAsset());
      }
    }
    return positions;
  }

  private ValuationData getValuationData(Positions positions, Collection<Asset> assets) {
    CompletableFuture<PriceResponse> futurePriceResponse = bcService.getMarketData(assets);
    FxRequest fxRequest = new FxUtils().getFxRequest(
        positions.getPortfolio().getBase(),
        positions);

    CompletableFuture<FxResponse> futureFxResponse = bcService.getFxData(fxRequest);

    PriceResponse priceResponse;
    FxResponse fxResponse;
    try {
      priceResponse = futurePriceResponse.get(30, SECONDS);
      fxResponse = futureFxResponse.get(30, SECONDS);
      return ValuationData.builder()
          .fxResponse(fxResponse)
          .priceResponse(priceResponse)
          .build();
    } catch (InterruptedException | ExecutionException e) {
      log.error(e.getMessage());
      throw new SystemException("Getting Market Data");
    } catch (TimeoutException e) {
      log.error(e.getMessage());
      throw new SystemException("Timeout getting market data");
    }

  }
}
