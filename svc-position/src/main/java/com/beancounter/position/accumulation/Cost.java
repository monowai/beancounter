package com.beancounter.position.accumulation;

import com.beancounter.common.helper.MathHelper;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.position.model.Position;
import java.math.BigDecimal;

public class Cost {
  private MathHelper mathHelper = new MathHelper();

  /**
   * Unit cost calculator.
   *
   * @param costBasis costAmount
   * @param total     current quantity
   * @return unit cost
   */
  BigDecimal average(BigDecimal costBasis, BigDecimal total) {
    return costBasis.divide(total, mathHelper.getMathContext());
  }

  void setCostValue(Position position, MoneyValues moneyValues) {
    QuantityValues quantityValues = position.getQuantityValues();
    moneyValues.setCostValue(
        mathHelper.multiply(moneyValues.getAverageCost(), quantityValues.getTotal()));
  }


}
