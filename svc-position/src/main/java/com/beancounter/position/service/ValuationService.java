package com.beancounter.position.service;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.accumulation.Gains;
import com.beancounter.position.accumulation.MarketValue;
import com.beancounter.position.model.FxReport;
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

  private AsyncMdService asyncMdService;

  @Autowired
  ValuationService(AsyncMdService asyncMdService) {
    this.asyncMdService = asyncMdService;
  }

  @Override
  public PositionResponse value(Positions positions) {

    if (positions == null) {
      return PositionResponse.builder()
          .data(null)
          .build();
    }
    if (positions.getAsAt() != null) {
      DateUtils.isValid(positions.getAsAt());
    }
    Collection<Asset> assets = new ArrayList<>();
    for (Position position : positions.getPositions().values()) {
      Gains.value(position, Position.In.PORTFOLIO);
      if (!position.getQuantityValues().getTotal().equals(BigDecimal.ZERO)) {
        assets.add(position.getAsset());
      }
    }

    return PositionResponse.builder()
        .data(value(positions, assets))
        .build();
  }

  private Positions value(Positions positions, Collection<Asset> assets) {
    // Anything to value?
    if (assets.isEmpty()) {
      return positions;
    }
    log.debug("Valuing {} positions...", positions.getPositions().size());

    FxReport fxReport = FxReport.builder()
        .base(positions.getPortfolio().getBase())
        .portfolio(positions.getPortfolio().getCurrency())
        .build();

    // Set market data into the positions
    ValuationData valuationData = getValuationData(positions, assets);
    FxResponse fxResponse = valuationData.getFxResponse();
    if (fxResponse == null) {
      throw new BusinessException("Unable to obtain FX Rates ");
    }
    Map<CurrencyPair, FxRate> rates = valuationData.getFxResponse().getData().getRates();

    if (valuationData.getPriceResponse() == null) {
      log.info("No prices found on date {}", positions.getAsAt());
      return positions; // Prevent NPE
    }

    for (MarketData marketData : valuationData.getPriceResponse().getData()) {
      Position position = positions.get(marketData.getAsset());
      if (!marketData.getClose().equals(BigDecimal.ZERO)) {
        MarketValue.value(position, fxReport, marketData, rates);
        position.setAsset(marketData.getAsset());
      }
    }
    return positions;
  }

  private ValuationData getValuationData(Positions positions, Collection<Asset> assets) {
    CompletableFuture<PriceResponse> futurePriceResponse =
        asyncMdService.getMarketData(
            PriceRequest.builder()
                .date(positions.getAsAt())
                .assets(assets).build()
        );

    FxRequest fxRequest = new FxUtils().getFxRequest(
        positions.getPortfolio().getBase(),
        positions);
    log.debug("Value request {}", fxRequest);
    CompletableFuture<FxResponse> futureFxResponse = asyncMdService.getFxData(fxRequest);

    return getValuationData(futurePriceResponse, futureFxResponse);

  }

  private ValuationData getValuationData(
      CompletableFuture<PriceResponse> futurePriceResponse,
      CompletableFuture<FxResponse> futureFxResponse) {
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
