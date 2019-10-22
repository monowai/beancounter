package com.beancounter.position.accumulation;

import static com.beancounter.position.utils.PositionUtils.getCurrency;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;

public class Dividend implements ValueTransaction {

  private MathUtils mathUtils = new MathUtils();

  public void value(Transaction transaction, Position position) {
    value(transaction, position, Position.In.TRADE, BigDecimal.ONE);
    value(transaction, position, Position.In.BASE, transaction.getTradeBaseRate());
    value(transaction, position, Position.In.PORTFOLIO, transaction.getTradePortfolioRate());
  }

  private void value(Transaction transaction,
                     Position position,
                     Position.In in,
                     BigDecimal rate) {

    MoneyValues moneyValues = position.getMoneyValue(in, getCurrency(in, transaction));
    moneyValues.setDividends(
        mathUtils.add(moneyValues.getDividends(),
            mathUtils.multiply(transaction.getTradeAmount(), rate)));

  }

}
