package com.beancounter.common.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Controls the way BC will deal with Division and Multiplication when it comes to Fx Rates.
 */
public class MathHelper {

  public BigDecimal divide(BigDecimal inValue, BigDecimal tradeRate) {
    if (tradeRate == null) {
      return inValue;
    }

    if (inValue.doubleValue() == 0d || tradeRate.doubleValue() == 0d) {
      return inValue;
    }
    return inValue.divide(tradeRate, RoundingMode.HALF_UP);
  }

  public BigDecimal multiply(BigDecimal inValue, BigDecimal tradeRate) {
    if (tradeRate == null) {
      return inValue;
    }

    if (inValue.doubleValue() == 0d || tradeRate.doubleValue() == 0d) {
      return inValue;
    }

    return inValue.multiply(tradeRate).abs().setScale(2, RoundingMode.HALF_UP);
  }
}
