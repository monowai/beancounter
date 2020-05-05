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
public class SellBehaviour implements AccumulationStrategy {
  private final CurrencyResolver currencyResolver = new CurrencyResolver();
  private final AverageCost averageCost = new AverageCost();

  public void accumulate(Trn trn, Portfolio portfolio, Position position) {
    BigDecimal soldQuantity = trn.getQuantity();
    if (soldQuantity.doubleValue() > 0) {
      // Sign the quantities
      soldQuantity = BigDecimal.ZERO.subtract(trn.getQuantity());
    }

    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setSold(quantityValues.getSold().add(soldQuantity));
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
        currencyResolver.resolve(in, portfolio, trn));
    moneyValues.setSales(
        moneyValues.getSales().add(
            MathUtils.divide(trn.getTradeAmount(), rate))
    );

    if (trn.getTradeAmount().compareTo(BigDecimal.ZERO) != 0) {
      BigDecimal unitCost = MathUtils.divide(trn.getTradeAmount(), rate)
          .divide(trn.getQuantity().abs(), MathUtils.getMathContext());
      BigDecimal unitProfit = unitCost.subtract(moneyValues.getAverageCost());
      BigDecimal realisedGain = unitProfit.multiply(trn.getQuantity().abs());
      moneyValues.setRealisedGain(MathUtils.add(moneyValues.getRealisedGain(), realisedGain));
    }

    if (position.getQuantityValues().getTotal().compareTo(BigDecimal.ZERO) == 0) {
      moneyValues.setCostBasis(BigDecimal.ZERO);
      moneyValues.setCostValue(BigDecimal.ZERO);
      moneyValues.setAverageCost(BigDecimal.ZERO);
      moneyValues.setMarketValue(BigDecimal.ZERO);
      moneyValues.setUnrealisedGain(BigDecimal.ZERO);
    }
    // If quantity changes, we need to update the cost Value
    averageCost.setCostValue(position, moneyValues);
  }
}
