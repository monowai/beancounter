package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestMathUtils {

  @Test
  void is_MultiplySafe() {

    assertThat(MathUtils.multiply(
        new BigDecimal("1000.00"),
        new BigDecimal("0")))
        .isEqualTo("1000.00");

    assertThat(MathUtils.multiply(
        new BigDecimal("1000.00"),
        new BigDecimal("0.00")))
        .isEqualTo("1000.00");

    assertThat(MathUtils.multiply(
        new BigDecimal("1000.00"),
        null))
        .isEqualTo("1000.00");

    assertThat(MathUtils.multiply(
        new BigDecimal("1000.00"),
        new BigDecimal("10.00")))
        .isEqualTo("10000.00");

    assertThat(MathUtils.multiply(
        null,
        new BigDecimal("10.00")))
        .isNull();

  }

  @Test
  void is_DivideSafe() {

    assertThat(MathUtils.divide(
        new BigDecimal("1000.00"),
        new BigDecimal("0")))
        .isEqualTo("1000.00");

    assertThat(MathUtils.divide(
        new BigDecimal("1000.00"),
        new BigDecimal("0.00")))
        .isEqualTo("1000.00");

    assertThat(MathUtils.divide(
        new BigDecimal("1000.00"),
        null))
        .isEqualTo("1000.00");

    assertThat(MathUtils.divide(
        null,
        new BigDecimal("10.00")))
        .isNull();

    assertThat(MathUtils.divide(new BigDecimal("1000.00"),
        new BigDecimal("10.00")))
        .isEqualTo("100.00");
  }

  @Test
  void is_ZeroAndNullSafe() {
    assertThat(MathUtils.isUnset(null)).isTrue();
    assertThat(MathUtils.isUnset(new BigDecimal("0"))).isTrue();
    assertThat(MathUtils.isUnset(new BigDecimal("0.00"))).isTrue();
  }

  @Test
  void is_AdditionWorkingToScale() {
    BigDecimal val = new BigDecimal("100.992");
    // HalfUp
    assertThat(MathUtils.add(val, val)).isEqualTo(new BigDecimal("201.98"));

    val = new BigDecimal("100.994");
    assertThat(MathUtils.add(val, val)).isEqualTo(new BigDecimal("201.99"));
  }
}
