package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import lombok.SneakyThrows;
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

  @Test
  void is_MathContextDividesToScale() {
    BigDecimal costBasis = new BigDecimal("1000");
    BigDecimal total = new BigDecimal("500.00");
    assertThat(costBasis.divide(total, MathUtils.getMathContext()))
        .isEqualTo(new BigDecimal("2"));

    total = new BigDecimal("555.00");
    assertThat(costBasis.divide(total, MathUtils.getMathContext()))
        .isEqualTo(new BigDecimal("1.801801802"));

  }

  @Test
  void is_NumberFormat() throws Exception {
    NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
    BigDecimal result = MathUtils.parse("1,000.99", numberFormat);
    assertThat(result).isEqualTo("1000.99");
  }

  @Test
  @SneakyThrows
  void is_CsvExportedQuotationsHandled() {
    NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
    String value = "\"1,180.74\"";
    assertThat(MathUtils.parse(value, numberFormat))
        .isEqualTo("1180.74");
  }
}
