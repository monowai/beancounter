package com.beancounter.position.accumulation;


import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.position.utils.CurrencyResolver;
import com.beancounter.position.valuation.AverageCost;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class BuyBehaviour implements AccumulationStrategy {
  private CurrencyResolver currencyResolver = new CurrencyResolver();
  private AverageCost averageCost = new AverageCost();

  public void accumulate(Trn trn, Portfolio portfolio, Position position) {
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

    MoneyValues moneyValues = position.getMoneyValues(
        in,
        currencyResolver.resolve(in, portfolio, trn)
    );

    moneyValues.setPurchases(moneyValues.getPurchases().add(
        MathUtils.multiply(trn.getTradeAmount(), rate))
    );

    moneyValues.setCostBasis(moneyValues.getCostBasis().add(
        MathUtils.multiply(trn.getTradeAmount(), rate))
    );

    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {

      moneyValues.setAverageCost(
          averageCost.value(moneyValues.getCostBasis(), position.getQuantityValues().getTotal())
      );

    }
    averageCost.setCostValue(position, moneyValues);
  }
}
