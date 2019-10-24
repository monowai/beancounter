package com.beancounter.position.accumulation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Cost {

  /**
   * Unit cost calculator.
   *
   * @param costBasis costAmount
   * @param total     current quantity
   * @return unit cost
   */
  BigDecimal average(BigDecimal costBasis, BigDecimal total) {
    return costBasis.divide(total, MathUtils.getMathContext());
  }

  void setCostValue(Position position, MoneyValues moneyValues) {
    QuantityValues quantityValues = position.getQuantityValues();
    moneyValues.setCostValue(
        MathUtils.multiply(moneyValues.getAverageCost(), quantityValues.getTotal()));
  }


}
