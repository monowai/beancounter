package com.beancounter.position.accumulation;

import static com.beancounter.position.utils.PositionUtils.getCurrency;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;

public class Dividend implements ValueTransaction {

  public void value(Transaction transaction, Portfolio portfolio, Position position) {
    value(transaction, portfolio, position, Position.In.TRADE, BigDecimal.ONE);
    value(transaction, portfolio, position, Position.In.BASE, transaction.getTradeBaseRate());
    value(transaction, portfolio, position, Position.In.PORTFOLIO,
        transaction.getTradePortfolioRate());
  }

  private void value(Transaction transaction,
                     Portfolio portfolio, Position position,
                     Position.In in,
                     BigDecimal rate) {

    MoneyValues moneyValues = position.getMoneyValues(in, getCurrency(in, portfolio, transaction));
    moneyValues.setDividends(
        MathUtils.add(moneyValues.getDividends(),
            MathUtils.multiply(transaction.getTradeAmount(), rate)));

  }

}
