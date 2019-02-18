package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Price;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.counter.Accumulator;
import com.beancounter.position.counter.TransactionConfiguration;
import com.beancounter.position.model.MarketValue;
import com.beancounter.position.model.Positions;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;


class TestMarketValues {

  @Test
  void marketValues() {
    Asset microsoft = Asset.builder()
        .code("MSFT")
        .market(Market.builder().code("NYSE").build())
        .build();

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    position = accumulator.accumulate(buy, position);
    positions.add(position);

    position = positions.get(microsoft);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(100))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));

    MarketValue marketValue = MarketValue.builder()
        .position(position)
        .price(Price.builder().asset(microsoft).price(new BigDecimal(100d)).build())
        .build();

    assertThat(marketValue)
        .hasFieldOrPropertyWithValue("marketValue", new BigDecimal(100 * 100d))
        .hasFieldOrPropertyWithValue("marketCost", new BigDecimal(2000d))
    ;

    // Add second buy
    accumulator.accumulate(buy, position);
    assertThat(marketValue)
        .hasFieldOrPropertyWithValue("marketValue", new BigDecimal(200 * 100d))
        .hasFieldOrPropertyWithValue("marketCost", new BigDecimal(4000d))
    ;

  }

  @Test
  void realisedGain() {
    Asset microsoft = Asset.builder()
        .code("MSFT")
        .market(Market.builder().code("NYSE").build())
        .build();

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    position = accumulator.accumulate(buy, position);
    positions.add(position);

    position = positions.get(microsoft);

    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(50)).build();

    position = accumulator.accumulate(sell, position);

    assertThat(position.getQuantityValues().getTotal()).isEqualTo(new BigDecimal(50));

    assertThat(position.getMoneyValues().getRealisedGain()).isEqualTo(new BigDecimal(1000d));

  }

  @Test
  void realisedGainAfterSellingToZero() {
    Asset microsoft = Asset.builder()
        .code("MSFT")
        .market(Market.builder().code("NYSE").build())
        .build();

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("1695.02"))
        .quantity(new BigDecimal("8")).build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    position = accumulator.accumulate(buy, position);
    positions.add(position);

    buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("405.21"))
        .quantity(new BigDecimal("2")).build();

    accumulator.accumulate(buy, position);

    position = positions.get(microsoft);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.TEN);

    assertThat(position.getMoneyValues())
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("210.023"))
        .hasFieldOrPropertyWithValue("realisedGain", BigDecimal.ZERO)
    ;

    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("841.63"))
        .quantity(new BigDecimal("3")).build();

    accumulator.accumulate(sell, position);

    // Sell does not affect the cost basis or average cost, but it will create a signed gain
    assertThat(position.getMoneyValues())
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("210.023"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("211.560"))
    ;

    sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("1871.01"))
        .quantity(new BigDecimal("7")).build();

    accumulator.accumulate(sell, position);

    // Sell down to 0; reset cost basis
    assertThat(position.getMoneyValues())
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("0"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("0"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("612.408"))
    ;


  }

  @Test
  void realisedGainAfterReenteringAPosition() {
    Asset intel = Asset.builder()
        .code("INTC")
        .market(Market.builder().code("NYSE").build())
        .build();

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(intel);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(intel)
        .tradeAmount(new BigDecimal("2646.08"))
        .quantity(new BigDecimal("80")).build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    position = accumulator.accumulate(buy, position);
    positions.add(position);

    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(intel)
        .tradeAmount(new BigDecimal("2273.9"))
        .quantity(new BigDecimal("80")).build();

    accumulator.accumulate(sell, position);

    position = positions.get(intel);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO);

    assertThat(position.getMoneyValues())
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("-372.1760"))
    ;

    BigDecimal previousGain = position.getMoneyValues().getRealisedGain();

    // Re-enter the position
    buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(intel)
        .tradeAmount(new BigDecimal("1603.32"))
        .quantity(new BigDecimal("60")).build();

    accumulator.accumulate(buy, position);

    position = positions.get(intel);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", buy.getQuantity());

    assertThat(position.getMoneyValues())
        .hasFieldOrPropertyWithValue("costBasis", buy.getTradeAmount())
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("26.722"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("-372.1760"))
    ;

    // Second sell taking us back to zero. Verify that the accumulated gains.

    sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(intel)
        .tradeAmount(new BigDecimal("1664.31"))
        .quantity(new BigDecimal("60")).build();

    accumulator.accumulate(sell, position);

    assertThat(position.getMoneyValues())
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("realisedGain", previousGain.add(new BigDecimal("60.99")))
    ;

  }
}
