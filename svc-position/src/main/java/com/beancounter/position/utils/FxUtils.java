package com.beancounter.position.utils;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.DateUtils;

public class FxUtils {
  private DateUtils dateUtils = new DateUtils();

  public FxRequest getFxRequest(Currency base, Positions positions) {
    if (positions.getAsAt() == null) {
      positions.setAsAt(dateUtils.today());
    }

    FxRequest fxRequest = FxRequest.builder()
        .rateDate(positions.getAsAt())
        .build();

    Currency portfolio = positions.getPortfolio().getCurrency();
    for (Position position : positions.getPositions().values()) {
      fxRequest.add(getPair(base, position));
      fxRequest.add(getPair(portfolio, position));
    }
    return fxRequest;
  }

  public CurrencyPair getPair(Currency from, Position position) {
    Currency to = position.getAsset().getMarket().getCurrency();
    if (from == null || to == null) {
      return null;
    }
    if (from.getCode().equalsIgnoreCase(to.getCode())) {
      return null;
    }
    return CurrencyPair.builder()
        .from(from.getCode())
        .to(to.getCode())
        .build();
  }
}
