package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;


class TestCurrency {
  @Test
  void is_CurrencySerializing() throws Exception {

    Currency currency = Currency.builder()
        .code("SomeId")
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
  void is_FromMapConverting() {
    Currency currency = Currency.builder()
        .id("TWEE")
        .code("CODE")
        .name("Name")
        .symbol("$")
        .build();

    Map<String, Object> mapCurrency = new HashMap<>();
    mapCurrency.put("code", currency.getCode());
    mapCurrency.put("name", currency.getName());
    mapCurrency.put("symbol", currency.getSymbol());

    Currency fromMap = Currency.of(currency.getId(), mapCurrency);
    assertThat(fromMap).isEqualToComparingFieldByField(currency);
    assertThat(fromMap.toString()).isEqualTo("Currency(code=CODE)");
  }

}
