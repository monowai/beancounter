package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestMathUtils {
  private MathUtils mathUtils = new MathUtils();

  @Test
  void is_MultiplySafe() {

    assertThat(mathUtils.multiply(
        new BigDecimal("1000.00"),
        new BigDecimal("0")))
        .isEqualTo("1000.00");

    assertThat(mathUtils.multiply(
        new BigDecimal("1000.00"),
        new BigDecimal("0.00")))
        .isEqualTo("1000.00");

    assertThat(mathUtils.multiply(
        new BigDecimal("1000.00"),
        null))
        .isEqualTo("1000.00");

    assertThat(mathUtils.multiply(
        new BigDecimal("1000.00"),
        new BigDecimal("10.00")))
        .isEqualTo("10000.00");

    assertThat(mathUtils.multiply(
        null,
        new BigDecimal("10.00")))
        .isNull();

  }

  @Test
  void is_DivideSafe() {

    assertThat(mathUtils.divide(
        new BigDecimal("1000.00"),
        new BigDecimal("0")))
        .isEqualTo("1000.00");

    assertThat(mathUtils.divide(
        new BigDecimal("1000.00"),
        new BigDecimal("0.00")))
        .isEqualTo("1000.00");

    assertThat(mathUtils.divide(
        new BigDecimal("1000.00"),
        null))
        .isEqualTo("1000.00");

    assertThat(mathUtils.divide(
        null,
        new BigDecimal("10.00")))
        .isNull();

    assertThat(mathUtils.divide(new BigDecimal("1000.00"),
        new BigDecimal("10.00")))
        .isEqualTo("100.00");
  }

  @Test
  void is_ZeroAndNullSafe() {
    assertThat(mathUtils.isUnset(null)).isTrue();
    assertThat(mathUtils.isUnset(new BigDecimal("0"))).isTrue();
    assertThat(mathUtils.isUnset(new BigDecimal("0.00"))).isTrue();
  }

  @Test
  void is_AdditionWorkingToScale() {
    BigDecimal val = new BigDecimal("100.992");
    // HalfUp
    assertThat(mathUtils.add(val, val)).isEqualTo(new BigDecimal("201.98"));

    val = new BigDecimal("100.994");
    assertThat(mathUtils.add(val, val)).isEqualTo(new BigDecimal("201.99"));
  }
}
