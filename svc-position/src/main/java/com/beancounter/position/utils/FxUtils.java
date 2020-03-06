package com.beancounter.position.utils;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.DateUtils;
import org.springframework.stereotype.Service;

@Service
public class FxUtils {
  private DateUtils dateUtils = new DateUtils();

  public FxRequest buildRequest(Currency base, Positions positions) {
    if (positions.getAsAt() == null) {
      positions.setAsAt(dateUtils.today());
    }

    FxRequest fxRequest = FxRequest.builder()
        .rateDate(positions.getAsAt())
        .build();

    Currency portfolio = positions.getPortfolio().getCurrency();
    for (Position position : positions.getPositions().values()) {
      fxRequest.add(IsoCurrencyPair.from(base, position.getAsset().getMarket().getCurrency()));
      fxRequest.add(IsoCurrencyPair.from(portfolio, position.getAsset().getMarket().getCurrency()));
    }
    return fxRequest;
  }

}
