package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.service.Accumulator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestAccumulationOfQuantityValues {

  @Test
  void is_TotalQuantityCorrect() {
    Portfolio portfolio = getPortfolio("TEST");
    Transaction buyTrn = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(getAsset("CODE", "marketCode"))
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator();

    Position position = Position.builder()
        .asset(buyTrn.getAsset())
        .build();

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO);

    position = accumulator.accumulate(buyTrn, portfolio, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(100))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

    position = accumulator.accumulate(buyTrn, portfolio, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("total", new BigDecimal(200));


    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(buyTrn.getAsset())
        .quantity(new BigDecimal(100)).build();

    position = accumulator.accumulate(sell, portfolio, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("sold", new BigDecimal(-100))
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

    position = accumulator.accumulate(sell, portfolio, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("sold", new BigDecimal(-200))
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(200))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(0));

  }
}
