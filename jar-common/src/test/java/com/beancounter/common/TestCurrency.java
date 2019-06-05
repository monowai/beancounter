package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


class TestCurrency {
  @Test
  void jsonSerialization() throws Exception {

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

}
