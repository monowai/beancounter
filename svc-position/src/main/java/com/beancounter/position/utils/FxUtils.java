package com.beancounter.position.utils;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;

public class FxUtils {

  public FxRequest getFxRequest(Currency base, Positions positions) {
    FxRequest fxRequest = FxRequest.builder().build();
    Currency portfolio = positions.getPortfolio().getCurrency();
    for (Position position : positions.getPositions().values()) {
      fxRequest.add(getPair(base, position));
      fxRequest.add(getPair(portfolio, position));
    }
    return fxRequest;
  }

  public CurrencyPair getPair(Currency from, Position position) {
    Currency to = position.getAsset().getMarket().getCurrency();
    if ( from == null || to == null ){
      return null;
    }
    if ( from.getCode().equalsIgnoreCase(to.getCode())) {
      return null;
    }
    return CurrencyPair.builder()
        .from(from.getCode())
        .to(to.getCode())
        .build();
  }
}
