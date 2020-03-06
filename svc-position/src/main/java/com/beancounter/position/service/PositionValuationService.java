package com.beancounter.position.service;

import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Positions;
import com.beancounter.position.model.ValuationData;
import com.beancounter.position.utils.FxUtils;
import com.beancounter.position.valuation.MarketValue;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PositionValuationService {
  private AsyncMdService asyncMdService;
  private MarketValue marketValue;
  private FxUtils fxUtils;

  PositionValuationService(
      AsyncMdService asyncMdService,
      MarketValue marketValue,
      FxUtils fxUtils) {
    this.asyncMdService = asyncMdService;
    this.marketValue = marketValue;
    this.fxUtils = fxUtils;
  }

  public Positions value(Positions positions, Collection<Asset> assets) {
    if (assets.isEmpty()) {
      return positions;
    }
    log.debug("Valuing {} positions...", positions.getPositions().size());

    // Set market data into the positions
    ValuationData valuationData = getValuationData(positions, assets);
    if (valuationData.getPriceResponse() == null) {
      log.info("No prices found on date {}", positions.getAsAt());
      return positions; // Prevent NPE
    }

    FxResponse fxResponse = valuationData.getFxResponse();
    if (fxResponse == null) {
      throw new BusinessException("Unable to obtain FX Rates ");
    }

    Map<IsoCurrencyPair, FxRate> rates = fxResponse.getData().getRates();

    for (MarketData marketData : valuationData.getPriceResponse().getData()) {
      if (!marketData.getClose().equals(BigDecimal.ZERO)) {
        marketValue.value(
            positions,
            marketData,
            rates);
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

    CompletableFuture<FxResponse> futureFxResponse =
        asyncMdService.getFxData(fxUtils.buildRequest(
            positions.getPortfolio().getBase(),
            positions));

    return asyncMdService.getValuationData(futurePriceResponse, futureFxResponse);

  }

}
