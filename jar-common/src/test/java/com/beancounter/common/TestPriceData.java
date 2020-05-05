package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.PriceData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class TestPriceData {

  @Test
  void is_PriceDataWithRates() {
    MarketData marketData = MarketData.builder()
        .changePercent(new BigDecimal("1.01"))
        .previousClose(new BigDecimal("1.00"))
        .change(new BigDecimal("1.00"))
        .open(new BigDecimal("2.00"))
        .close(new BigDecimal("2.00"))
        .build();

    PriceData noFx = PriceData.of(marketData, new BigDecimal("1.0"));
    assertThat(noFx)
        .hasFieldOrPropertyWithValue("open", marketData.getOpen())
        .hasFieldOrPropertyWithValue("previousClose", marketData.getPreviousClose())
        .hasFieldOrPropertyWithValue("close", marketData.getClose())
        .hasFieldOrPropertyWithValue("change", marketData.getChange())
        .hasFieldOrPropertyWithValue("changePercent", marketData.getChangePercent());

    PriceData withFx = PriceData.of(marketData, new BigDecimal("2.0"));
    assertThat(withFx)
        .hasFieldOrPropertyWithValue("open", new BigDecimal("4.00"))
        .hasFieldOrPropertyWithValue("close", new BigDecimal("4.00"))
        .hasFieldOrPropertyWithValue("previousClose", new BigDecimal("2.00"))
        .hasFieldOrPropertyWithValue("change", new BigDecimal("2.00"))
        .hasFieldOrPropertyWithValue("changePercent", new BigDecimal("0.5000"));
  }

  @Test
  void is_ChangeWithRatesComputing() {
    MarketData marketData = MarketData.builder()
        .previousClose(new BigDecimal("40.92"))
        .close(new BigDecimal("41.35"))
        .change(new BigDecimal("0.43"))
        .changePercent(new BigDecimal("0.0104"))
        .build();

    PriceData noFx = PriceData.of(marketData, new BigDecimal("1.0"));
    assertThat(noFx)
        .hasFieldOrPropertyWithValue("previousClose", marketData.getPreviousClose())
        .hasFieldOrPropertyWithValue("close", marketData.getClose())
        .hasFieldOrPropertyWithValue("change", marketData.getChange())
        .hasFieldOrPropertyWithValue("changePercent", marketData.getChangePercent());

    PriceData withFx = PriceData.of(marketData, new BigDecimal("2.0"));
    assertThat(withFx)
        .hasFieldOrPropertyWithValue("previousClose", new BigDecimal("81.84"))
        .hasFieldOrPropertyWithValue("close", new BigDecimal("82.70"))
        .hasFieldOrPropertyWithValue("change", new BigDecimal("0.86"))
        .hasFieldOrPropertyWithValue("changePercent", new BigDecimal("0.0104"))
    ;

  }
  @Test
  void is_PriceDataNullOk() {

    PriceData withFx = PriceData.of(
        MarketData.builder()
            .changePercent(new BigDecimal("1.01"))
            .previousClose(null)
            .change(new BigDecimal("1.00"))
            .open(new BigDecimal("2.00"))
            .close(new BigDecimal("2.00"))
            .build(),
        new BigDecimal("1.1"));

    assertThat(withFx).isNotNull();

    withFx = PriceData.of(
        MarketData.builder()
            .changePercent(new BigDecimal("1.01"))
            .previousClose(new BigDecimal("1.00"))
            .change(new BigDecimal("1.00"))
            .open(new BigDecimal("2.00"))
            .close(null)
            .build(),
        new BigDecimal("1.1"));
    assertThat(withFx).isNotNull();

  }
}