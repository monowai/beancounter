package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.utils.CurrencyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class TestCurrency {
  private final CurrencyUtils currencyUtils = new CurrencyUtils();

  @Test
  void is_CurrencySerializing() throws Exception {

    Currency currency = Currency.builder()
        .code("SomeCode")
        .name("Some Name")
        .symbol("$")
        .build();

    assertThat(currency).isNotNull();

    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(currency);

    Currency fromJson = om.readValue(json, Currency.class);

    assertThat(fromJson).isEqualTo(currency);
  }

  @Test
  void is_CurrencyResponseSerializing() throws Exception {

    CurrencyResponse currencyResponse = CurrencyResponse.builder().build();
    Collection<Currency> currencies = new ArrayList<>();
    Currency currency = Currency.builder()
        .code("SomeId")
        .name("Some Name")
        .symbol("$")
        .build();

    currencies.add(currency);
    currencyResponse.setData(currencies);

    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(currencyResponse);

    CurrencyResponse fromJson = om.readValue(json, CurrencyResponse.class);

    assertThat(fromJson).isEqualTo(currencyResponse);
  }

  @Test
  void is_NullCurrencyCodeWorking() {
    Assertions.assertThrows(NullPointerException.class, ()
        -> Currency.builder().code(null).build());
  }

  @Test
  void is_GetCurrencyWorking() {
    Currency currency = currencyUtils.getCurrency("NZD");
    assertThat(currency).hasFieldOrPropertyWithValue("code", "NZD");
    assertThat(currencyUtils.getCurrency(null)).isNull();
  }

  @Test
  void is_CurrencyPairConsistent() {
    String trade = "NZD";
    String report = "USD";
    IsoCurrencyPair byCode = IsoCurrencyPair.builder()
        .from(report)
        .to(trade)
        .build();

    IsoCurrencyPair byCurrency = IsoCurrencyPair.from(
        currencyUtils.getCurrency(report), currencyUtils.getCurrency(trade));
    assertThat(byCode).isEqualToComparingFieldByField(byCurrency);

    assertThat(IsoCurrencyPair.from(
        currencyUtils.getCurrency(report), currencyUtils.getCurrency(report)))
        .isNull();

  }

  @Test
  void is_NullCurrencyAssertions() {

    assertThat(IsoCurrencyPair.from(
        currencyUtils.getCurrency("USD"), null))
        .isNull();
    assertThat(IsoCurrencyPair.from(
        null, currencyUtils.getCurrency("USD")))
        .isNull();
    assertThat(currencyUtils
        .getCurrencyPair(BigDecimal.ONE,
            currencyUtils.getCurrency("USD"), null))
        .isNull();
    assertThat(currencyUtils.getCurrencyPair(BigDecimal.ONE,
        null, currencyUtils.getCurrency("USD")))
        .isNull();

  }


  @Test
  void is_CorrectCurrencyPair() {
    assertThat(currencyUtils.getCurrencyPair(null, null, null)).isNull();

    assertThat(currencyUtils.getCurrencyPair(BigDecimal.TEN,
        currencyUtils.getCurrency("NZD"),
        currencyUtils.getCurrency("USD")))
        .isNull(); // We have a rate, so don't request one

    assertThat(currencyUtils.getCurrencyPair(null,
        currencyUtils.getCurrency("USD"), currencyUtils.getCurrency("USD")))
        .isNull();

    assertThat(currencyUtils.getCurrencyPair(null,
        currencyUtils.getCurrency("NZD"), currencyUtils.getCurrency("USD")))
        .isNotNull()
        .hasFieldOrPropertyWithValue("from", "NZD")
        .hasFieldOrPropertyWithValue("to", "USD");

  }

}
