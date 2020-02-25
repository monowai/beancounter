package com.beancounter.position.service;

import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Positions;
import com.beancounter.position.accumulation.MarketValue;
import com.beancounter.position.model.FxReport;
import com.beancounter.position.model.ValuationData;
import com.beancounter.position.utils.FxUtils;
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

  PositionValuationService(AsyncMdService asyncMdService) {
    this.asyncMdService = asyncMdService;
  }

  public Positions value(Positions positions, Collection<Asset> assets) {
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
      com.beancounter.common.model.Position position = positions.get(marketData.getAsset());
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

    CompletableFuture<FxResponse> futureFxResponse =
        asyncMdService.getFxData(new FxUtils().getFxRequest(
            positions.getPortfolio().getBase(),
            positions));

    return asyncMdService.getValuationData(futurePriceResponse, futureFxResponse);

  }

}
