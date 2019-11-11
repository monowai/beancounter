package com.beancounter.position.utils;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PositionUtils {
  public static Currency getCurrency(Position.In in, Transaction transaction) {

    if (in.equals(Position.In.TRADE)) {
      return transaction.getTradeCurrency();
    } else if (in.equals(Position.In.PORTFOLIO)) {
      return transaction.getPortfolio().getCurrency();
    } else {
      return transaction.getPortfolio().getBase();
    }
  }
}
