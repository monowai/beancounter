package com.beancounter.position.accumulation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Trn;
import com.beancounter.position.utils.CurrencyResolver;
import com.beancounter.position.valuation.AverageCost;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class SplitBehaviour implements AccumulationStrategy {
  private final AverageCost averageCost = new AverageCost();

  private final CurrencyResolver currencyResolver = new CurrencyResolver();

  public void accumulate(Trn trn, Portfolio portfolio, Position position) {
    BigDecimal total = position.getQuantityValues().getTotal();
    position.getQuantityValues()
        .setAdjustment((trn.getQuantity().multiply(total)).subtract(total));

    value(position, position.getMoneyValues(Position.In.TRADE,
        currencyResolver.resolve(Position.In.TRADE, portfolio, trn)));
    value(position, position.getMoneyValues(Position.In.BASE,
        currencyResolver.resolve(Position.In.BASE, portfolio, trn)));
    value(position, position.getMoneyValues(Position.In.PORTFOLIO,
        currencyResolver.resolve(Position.In.PORTFOLIO, portfolio, trn)));
  }

  private void value(Position position, MoneyValues moneyValues) {
    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {
      moneyValues.setAverageCost(
          averageCost.value(moneyValues.getCostBasis(), position.getQuantityValues().getTotal())
      );
    }

    averageCost.setCostValue(position, moneyValues);
  }
}
