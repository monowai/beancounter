package com.beancounter.position.valuation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class AverageCost {

  /**
   * Unit cost calculator.
   *
   * @param costBasis accumulation of all trn values that affect cost
   * @param total     current quantity
   * @return unit cost
   */
  public BigDecimal value(BigDecimal costBasis, BigDecimal total) {
    return costBasis.divide(total, MathUtils.getMathContext());
  }

  public void setCostValue(Position position, MoneyValues moneyValues) {
    QuantityValues quantityValues = position.getQuantityValues();
    moneyValues.setCostValue(
        MathUtils.multiply(moneyValues.getAverageCost(), quantityValues.getTotal()));
  }


}
