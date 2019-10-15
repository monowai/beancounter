package com.beancounter.position.accumulation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.position.model.Position;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class Sell implements AccumulationLogic {

  private MathUtils mathUtils = new MathUtils();
  private Cost cost = new Cost();

  public void value(Transaction transaction, Position position) {
    BigDecimal soldQuantity = transaction.getQuantity();
    if (soldQuantity.doubleValue() > 0) {
      // Sign the quantities
      soldQuantity = new BigDecimal(0 - transaction.getQuantity().doubleValue());
    }

    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setSold(quantityValues.getSold().add(soldQuantity));
    value(transaction, position, Position.In.TRADE, BigDecimal.ONE);
    value(transaction, position, Position.In.BASE, transaction.getTradeBaseRate());
    value(transaction, position, Position.In.CASH, transaction.getTradeCashRate());
    value(transaction, position, Position.In.PORTFOLIO, transaction.getTradePortfolioRate());

  }

  private void value(Transaction transaction,
                     Position position,
                     Position.In in,
                     BigDecimal rate) {

    MoneyValues moneyValues = position.getMoneyValue(in);
    moneyValues.setSales(
        moneyValues.getSales().add(
            mathUtils.multiply(transaction.getTradeAmount(), rate))
    );

    if (!transaction.getTradeAmount().equals(BigDecimal.ZERO)) {
      BigDecimal unitCost = mathUtils.multiply(transaction.getTradeAmount(), rate)
          .divide(transaction.getQuantity().abs(), mathUtils.getMathContext());
      BigDecimal unitProfit = unitCost.subtract(moneyValues.getAverageCost());
      BigDecimal realisedGain = unitProfit.multiply(transaction.getQuantity().abs());
      moneyValues.setRealisedGain(mathUtils.add(moneyValues.getRealisedGain(), realisedGain));
    }

    if (position.getQuantityValues().getTotal().equals(BigDecimal.ZERO)) {
      moneyValues.setCostBasis(BigDecimal.ZERO);
      moneyValues.setCostValue(BigDecimal.ZERO);
      moneyValues.setAverageCost(BigDecimal.ZERO);
    }
    // If quantity changes, we need to update the cost Value
    cost.setCostValue(position, moneyValues);
  }
}
