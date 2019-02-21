package com.beancounter.position;

import static com.beancounter.position.TestUtils.convert;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.counter.Accumulator;
import com.beancounter.position.counter.TransactionConfiguration;
import com.beancounter.position.model.Positions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Corporate Actions - Stock Splits.  These do not affect Cost.
 * @author mikeh
 * @since 2019-02-20
 */
class TestStockSplits {

  @Test
  void splitOccurs() {
    Asset apple = Asset.builder()
        .code("AAPL")
        .market(Market.builder().code("NASDAQ").build())
        .build();

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(apple);

    assertThat(position)
        .isNotNull();

    LocalDate today = LocalDate.now();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(apple)
        .tradeAmount(new BigDecimal("2000"))
        .tradeDate(convert(today))
        .quantity(new BigDecimal("100")).build();

    Transaction stockSplit = Transaction.builder()
        .trnType(TrnType.SPLIT)
        .asset(apple)
        .tradeDate(convert(today))
        .quantity(new BigDecimal("7")).build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    positions.add(position);
    position = accumulator.accumulate(buy, position);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100))
    ;

    BigDecimal costBasis = position.getMoneyValues().getCostBasis();
    BigDecimal averageCost = position.getMoneyValues().getAverageCost();

    accumulator.accumulate(stockSplit, position);

    // 7 for one split
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(700))
    ;

    assertThat(position.getMoneyValues())
        .hasFieldOrPropertyWithValue("averageCost", averageCost)
        .hasFieldOrPropertyWithValue("costBasis", costBasis)
    ;

    // Another buy at the adjusted price
    accumulator.accumulate(buy, position);

    // 7 for one split
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(800))
    ;

    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(apple)
        .tradeAmount(new BigDecimal("2000"))
        .tradeDate(convert(today))
        .quantity(new BigDecimal("800")).build();

    // Sell the entire position
    accumulator.accumulate(sell, position);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO)
    ;

    // Repurchase; total should be equal to the quantity we just purchased
    accumulator.accumulate(buy, position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", buy.getQuantity())
    ;

  }
}
