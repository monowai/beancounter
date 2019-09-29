package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.helper.MathHelper;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestMathHelper {

  @Test
  @VisibleForTesting
  void is_RateInputForFxSafe() {
    MathHelper mathHelper = new MathHelper();

    assertThat(mathHelper.multiply(new BigDecimal("1000.00"),
        new BigDecimal("0")))
        .isEqualTo("1000.00");

    assertThat(mathHelper.multiply(new BigDecimal("1000.00"),
        new BigDecimal("0.00")))
        .isEqualTo("1000.00");

    assertThat(mathHelper.multiply(new BigDecimal("1000.00"),
        null))
        .isEqualTo("1000.00");

    assertThat(mathHelper.multiply(new BigDecimal("1000.00"),
        new BigDecimal("10.00")))
        .isEqualTo("10000.00");
  }

  @Test
  @VisibleForTesting
  void is_RateInputForSaveDivide() {
    MathHelper mathHelper = new MathHelper();

    assertThat(mathHelper.divide(new BigDecimal("1000.00"),
        new BigDecimal("0")))
        .isEqualTo("1000.00");

    assertThat(mathHelper.divide(new BigDecimal("1000.00"),
        new BigDecimal("0.00")))
        .isEqualTo("1000.00");

    assertThat(mathHelper.divide(new BigDecimal("1000.00"),
        null))
        .isEqualTo("1000.00");

    assertThat(mathHelper.divide(new BigDecimal("1000.00"),
        new BigDecimal("10.00")))
        .isEqualTo("100.00");
  }
}
