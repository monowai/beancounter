package com.beancounter.position.accumulation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;

public class Cost {
  private MathUtils mathUtils = new MathUtils();

  /**
   * Unit cost calculator.
   *
   * @param costBasis costAmount
   * @param total     current quantity
   * @return unit cost
   */
  BigDecimal average(BigDecimal costBasis, BigDecimal total) {
    return costBasis.divide(total, mathUtils.getMathContext());
  }

  void setCostValue(Position position, MoneyValues moneyValues) {
    QuantityValues quantityValues = position.getQuantityValues();
    moneyValues.setCostValue(
        mathUtils.multiply(moneyValues.getAverageCost(), quantityValues.getTotal()));
  }


}
