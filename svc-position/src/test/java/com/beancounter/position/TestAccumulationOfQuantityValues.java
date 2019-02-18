package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.counter.Accumulator;
import com.beancounter.position.counter.TransactionConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestAccumulationOfQuantityValues {

  @Test
  void quantitiesTotalCorrectly() {
    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());

    Position position = Position.builder().build();

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO);

    position = accumulator.accumulate(buy, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(100))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

    position = accumulator.accumulate(buy, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("total", new BigDecimal(200));


    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .quantity(new BigDecimal(100)).build();

    position = accumulator.accumulate(sell, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("sold", new BigDecimal(-100))
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

    position = accumulator.accumulate(sell, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("sold", new BigDecimal(-200))
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(0));

  }
}
