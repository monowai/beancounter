package com.beancounter.position;

import static com.beancounter.common.helper.AssetHelper.getAsset;
import static org.assertj.core.api.Java6Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import com.beancounter.position.service.Accumulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;


class TestPositions {

  @Test
  @VisibleForTesting
  void jsonSerialization() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Map<Position.In, MoneyValues> moneyValuesMap = new HashMap<>();
    moneyValuesMap.put(Position.In.TRADE, MoneyValues.builder()
        .dividends(new BigDecimal(100d))
        .build());

    Position position = Position.builder()
        .moneyValues(moneyValuesMap)
        .quantityValues(QuantityValues.builder()
            .purchased(new BigDecimal(200))
            .build())
        .build();

    String json = mapper.writeValueAsString(position);

    Position fromJson = mapper.readValue(json, Position.class);

    assertThat(fromJson).isEqualToComparingFieldByField(position);
  }

  @Test
  @VisibleForTesting
  void accumulateCashDividend() {

    Asset asset = getAsset("MO", "NYSE");

    //
    Transaction transaction = Transaction.builder()
        .asset(asset)
        .trnType(TrnType.DIVI)
        .tradeCurrency(Currency.builder().code("AUD").build())
        .tradeCashRate(new BigDecimal("0.8988"))
        .tradeAmount(new BigDecimal("12.99"))
        .build();

    Accumulator accumulator = new Accumulator(
    );

    Positions positions = new Positions(Portfolio.builder().code("test").build());
    Position position = positions.get(asset);
    accumulator.accumulate(transaction, position);
    assertThat(position.getMoneyValue(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("dividends", transaction.getTradeAmount());

  }
}
