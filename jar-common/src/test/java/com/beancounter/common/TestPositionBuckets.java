package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.PortfolioUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TestPositionBuckets {

  @Test
  void is_DefaultMoneyValuesSet() {
    MoneyValues moneyValues = MoneyValues.builder()
        .currency(Currency.builder().code("USD").build())
        .build();

    assertThat(moneyValues).hasNoNullFieldsOrPropertiesExcept("priceData");
  }

  @Test
  void is_PositionSerializing() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    PositionRequest positionRequest = PositionRequest.builder()
        .portfolioId("ABC")
        .trns(new ArrayList<>())
        .build();

    String json = objectMapper.writeValueAsString(positionRequest);
    assertThat(objectMapper.readValue(json, PositionRequest.class))
        .isEqualToComparingFieldByField(positionRequest);

    PositionResponse positionResponse = PositionResponse.builder()
        .data(new Positions(PortfolioUtils.getPortfolio("ABC")))
        .build();

    json = objectMapper.writeValueAsString(positionResponse);
    assertThat(objectMapper.readValue(json, PositionResponse.class))
        .isEqualToComparingFieldByField(positionResponse);

  }
}
