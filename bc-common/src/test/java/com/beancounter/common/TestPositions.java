package com.beancounter.common;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Quantity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;



class TestPositions {

  @Test
  void jsonSerialization() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    Position position = Position.builder()
        .moneyValues(MoneyValues.builder()
            .dividends(new BigDecimal(100d))
            .build())
        .quantity(Quantity.builder()
            .purchased(new BigDecimal(200))
            .build())
        .portfolio(Portfolio.builder()
            .id("Mike")
            .build())
        .build();

    String json = mapper.writeValueAsString(position);

    Position fromJson = mapper.readValue(json, Position.class);

    assertThat(fromJson).isEqualToComparingFieldByField(position);
  }
}
