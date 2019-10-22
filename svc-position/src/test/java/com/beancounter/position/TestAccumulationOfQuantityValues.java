package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.position.TestUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.service.Accumulator;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestAccumulationOfQuantityValues {

  @Test
  @VisibleForTesting
  void is_TotalQuantityCorrect() {
    Transaction buyTrn = Transaction.builder()
        .trnType(TrnType.BUY)
        .portfolio(getPortfolio("TEST"))
        .asset(getAsset("CODE", "marketCode"))
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator();

    Position position = Position.builder()
        .asset(buyTrn.getAsset())
        .build();

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO);

    position = accumulator.accumulate(buyTrn, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(100))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

    position = accumulator.accumulate(buyTrn, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("total", new BigDecimal(200));


    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .portfolio(buyTrn.getPortfolio())
        .asset(buyTrn.getAsset())
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
