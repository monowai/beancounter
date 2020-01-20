package com.beancounter.position.accumulation;

import static com.beancounter.position.utils.PositionUtils.getCurrency;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;

public class Buy implements ValueTransaction {

  public void value(Trn trn, Portfolio portfolio, Position position) {
    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setPurchased(quantityValues.getPurchased().add(trn.getQuantity()));

    value(trn, portfolio, position, Position.In.TRADE, BigDecimal.ONE);
    value(trn, portfolio, position, Position.In.BASE, trn.getTradeBaseRate());
    value(trn, portfolio, position, Position.In.PORTFOLIO,
        trn.getTradePortfolioRate());

  }

  private void value(Trn trn,
                     Portfolio portfolio,
                     Position position,
                     Position.In in,
                     BigDecimal rate) {

    MoneyValues moneyValues = position.getMoneyValues(in, getCurrency(in, portfolio, trn));

    moneyValues.setPurchases(moneyValues.getPurchases().add(
        MathUtils.multiply(trn.getTradeAmount(), rate))
    );

    moneyValues.setCostBasis(moneyValues.getCostBasis().add(
        MathUtils.multiply(trn.getTradeAmount(), rate))
    );

    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {

      moneyValues.setAverageCost(
          Cost.average(moneyValues.getCostBasis(), position.getQuantityValues().getTotal())
      );

    }
    Cost.setCostValue(position, moneyValues);
  }
}
