package com.beancounter.position;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.config.TransactionConfiguration;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import com.beancounter.position.service.Accumulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;


class TestPositions {

  @Test
  void jsonSerialization() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Map<Position.In, MoneyValues> moneyValuesMap = new HashMap<>();
    moneyValuesMap.put(Position.In.LOCAL, MoneyValues.builder()
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
  void accumulateCashDividend() {

    Asset asset = Asset.builder()
        .code("MO")
        .market(Market.builder().code("NYSE").build())
        .build();

    //
    Transaction transaction = Transaction.builder()
        .asset(asset)
        .trnType(TrnType.DIVI)
        .tradeCurrency("AUD")
        .tradeRate(new BigDecimal("0.8988"))
        .tradeAmount(new BigDecimal("12.99"))
        .build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    Positions positions = new Positions(Portfolio.builder().code("test").build());
    Position position = positions.get(asset);
    accumulator.accumulate(transaction, position);
    assertThat(position.getMoneyValue(Position.In.LOCAL))
        .hasFieldOrPropertyWithValue("dividends", transaction.getTradeAmount());

  }
}
