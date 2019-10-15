package com.beancounter.common.utils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Controls the way BC will deal with Division and Multiplication when it comes to Fx Rates.
 */
public class MathUtils {

  private MathContext mathContext = new MathContext(10);

  public BigDecimal divide(BigDecimal money, BigDecimal rate) {
    if (isUnset(rate)) {
      return money;
    }

    if (isUnset(money)) {
      return money;
    }
    return money.divide(rate, RoundingMode.HALF_UP);
  }

  public BigDecimal multiply(BigDecimal money, BigDecimal rate) {
    if (isUnset(rate)) {
      return money;
    }

    if (isUnset(money)) {
      return money;
    }

    return money.multiply(rate).abs().setScale(2, RoundingMode.HALF_UP);
  }

  // Null and Zero are treated as "unSet"
  public boolean isUnset(BigDecimal value) {
    return value == null || BigDecimal.ZERO.compareTo(value) == 0;
  }

  public MathContext getMathContext() {
    return mathContext;
  }

  public BigDecimal add(BigDecimal value, BigDecimal amount) {
    return value.add(amount).setScale(2, RoundingMode.HALF_UP);

  }
}
