package com.beancounter.common;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Java6Assertions.assertThat;

import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Asset;
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

class TestPositions {

  @Test
  void is_PostionResponseChainSerializing() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Map<Position.In, MoneyValues> moneyValuesMap = new HashMap<>();
    moneyValuesMap.put(Position.In.TRADE, MoneyValues.builder()
        .dividends(new BigDecimal(100d))
        .build());

    Positions positions = new Positions(Portfolio.builder()
        .code("T")
        .currency(Currency.builder().code("SGD").build())
        .build());

    positions.add(Position.builder()
        .asset(getAsset("TEST", "TEST"))
        .moneyValues(moneyValuesMap)
        .quantityValues(QuantityValues.builder()
            .purchased(new BigDecimal(200))
            .build())
        .build()
    );

    PositionResponse positionResponse = PositionResponse.builder().data(positions).build();
    String json = mapper.writeValueAsString(positionResponse);

    PositionResponse fromJson = mapper.readValue(json, PositionResponse.class);

    assertThat(fromJson).isEqualToComparingFieldByField(positionResponse);
  }

  @Test
  void is_GetPositionNonNull() {

    Positions positions = new Positions(getPortfolio("Test"));
    Asset asset = getAsset("TEST", "TEST");
    Position position = positions.get(asset);
    assertThat(position).isNotNull().hasFieldOrPropertyWithValue("asset", asset);

  }

  @Test
  void is_MoneyValuesFromPosition () {

    Position position = Position.builder().asset(getAsset("Twee", "Twee"))
        .build();

    // Requesting a non existent MV.  Without a currency, it can't be created
    assertThat(position.getMoneyValue(Position.In.TRADE)).isNull();
    // Retrieve with a currency will create if missing
    assertThat(position.getMoneyValue(Position.In.TRADE, getCurrency("SGD")))
        .isNotNull()
        .hasFieldOrPropertyWithValue("currency", getCurrency("SGD"));
  }
}
