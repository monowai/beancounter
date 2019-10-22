package com.beancounter.common;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static org.assertj.core.api.Java6Assertions.assertThat;

import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.QuantityValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestValuation {

  @Test
  void is_PostionResponseChainSerializing() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Map<Position.In, MoneyValues> moneyValuesMap = new HashMap<>();
    moneyValuesMap.put(Position.In.TRADE, MoneyValues.builder()
        .dividends(new BigDecimal(100d))
        .build());

    Position position = Position.builder()
        .asset(getAsset("TEST", "TEST"))
        .moneyValues(moneyValuesMap)
        .quantityValues(QuantityValues.builder()
            .purchased(new BigDecimal(200))
            .build())
        .build();

    Positions positions = new Positions(Portfolio.builder()
        .code("T")
        .currency(Currency.builder().code("SGD").build())
        .build());
    positions.add(position);

    PositionResponse positionResponse = PositionResponse.builder().data(positions).build();
    String json = mapper.writeValueAsString(positionResponse);

    PositionResponse fromJson = mapper.readValue(json, PositionResponse.class);

    assertThat(fromJson).isEqualToComparingFieldByField(positionResponse);
  }
}
