package com.beancounter.position.utils;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;

public class PositionUtils {
  public static Currency getCurrency(Position.In in, Transaction transaction) {

    if (in.equals(Position.In.TRADE)) {
      return transaction.getAsset().getMarket().getCurrency();
    } else if (in.equals(Position.In.PORTFOLIO)) {
      return transaction.getPortfolio().getCurrency();
    } else {
      return transaction.getPortfolio().getBase();
    }
  }
}