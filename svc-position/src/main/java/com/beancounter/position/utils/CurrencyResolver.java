package com.beancounter.position.utils;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Trn;

public class CurrencyResolver {
  public Currency resolve(Position.In in, Portfolio portfolio, Trn trn) {

    if (in.equals(Position.In.TRADE)) {
      return trn.getTradeCurrency();
    } else if (in.equals(Position.In.PORTFOLIO)) {
      return portfolio.getCurrency();
    } else {
      return portfolio.getBase();
    }
  }
}
