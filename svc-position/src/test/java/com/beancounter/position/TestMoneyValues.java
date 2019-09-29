package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.config.TransactionConfiguration;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import com.beancounter.position.service.Accumulator;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;


class TestMoneyValues {

  @Test
  @VisibleForTesting
  void is_QuantityAndMarketValueCalculated() {
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


  }

  @Test
  @VisibleForTesting
  void is_RealisedGainCalculated() {
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

    assertThat(position.getMoneyValue(Position.In.LOCAL).getRealisedGain())
        .isEqualTo(new BigDecimal("1000.00"));

  }

  @Test
  @VisibleForTesting
  void is_RealisedGainWithSignedQuantitiesCalculated() {
    Asset bidu = Asset.builder()
        .code("BIDU")
        .market(Market.builder().code("NYSE").build())
        .build();

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(bidu);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(bidu)
        .tradeAmount(new BigDecimal("1695.02"))
        .quantity(new BigDecimal("8"))
        .build();

    Accumulator accumulator = new Accumulator(new TransactionConfiguration());
    position = accumulator.accumulate(buy, position);
    positions.add(position);

    position = positions.get(bidu);

    buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(bidu)
        .tradeAmount(new BigDecimal("405.21"))
        .quantity(new BigDecimal("2"))
        .build();

    position = accumulator.accumulate(buy, position);

    MoneyValues localMoney = position.getMoneyValue(Position.In.LOCAL);

    assertThat(position.getQuantityValues().getTotal().multiply(localMoney.getAverageCost())
        .setScale(2, RoundingMode.HALF_UP))
        .isEqualTo(localMoney.getCostBasis());


    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(bidu)
        .tradeAmount(new BigDecimal("841.63"))
        .quantity(new BigDecimal("-3")).build();

    position = accumulator.accumulate(sell, position);

    assertThat(position.getQuantityValues().getTotal().multiply(localMoney.getAverageCost())
        .setScale(2, RoundingMode.HALF_UP))
        .isEqualTo(localMoney.getCostValue());

    assertThat(localMoney)
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("sales", new BigDecimal("841.63"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("211.56"))
    ;

    sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(bidu)
        .tradeAmount(new BigDecimal("1871.01"))
        .quantity(new BigDecimal("-7")).build();

    position = accumulator.accumulate(sell, position);

    assertThat(position.getMoneyValue(Position.In.LOCAL))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("sales", new BigDecimal("2712.64"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("612.41"))
    ;

    assertThat(position.getQuantityValues().getTotal()).isEqualTo(BigDecimal.ZERO);

  }

  @Test
  @VisibleForTesting
  void is_RealisedGainAfterSellingToZeroCalculated() {
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

    assertThat(position.getMoneyValue(Position.In.LOCAL))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("210.023"))
        .hasFieldOrPropertyWithValue("realisedGain", BigDecimal.ZERO)
    ;

    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("841.63"))
        .quantity(new BigDecimal("3.0")).build();

    accumulator.accumulate(sell, position);

    // Sell does not affect the cost basis or average cost, but it will create a signed gain
    assertThat(position.getMoneyValue(Position.In.LOCAL))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("210.023"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("211.56"))
    ;

    sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("1871.01"))
        .quantity(new BigDecimal("7")).build();

    accumulator.accumulate(sell, position);

    // Sell down to 0; reset cost basis
    assertThat(position.getMoneyValue(Position.In.LOCAL))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("0"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("0"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("612.41"))
    ;


  }

  @Test
  @VisibleForTesting
  void is_RealisedGainAfterReenteringAPositionCalculated() {
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

    assertThat(position.getMoneyValue(Position.In.LOCAL))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("-372.18"))
    ;

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

    assertThat(position.getMoneyValue(Position.In.LOCAL))
        .hasFieldOrPropertyWithValue("costBasis", buy.getTradeAmount())
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("26.722"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("-372.18"))
    ;

    // Second sell taking us back to zero. Verify that the accumulated gains.

    sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(intel)
        .tradeAmount(new BigDecimal("1664.31"))
        .quantity(new BigDecimal("60")).build();

    BigDecimal previousGain = position.getMoneyValue(Position.In.LOCAL)
        .getRealisedGain(); // Track the previous gain

    accumulator.accumulate(sell, position);

    assertThat(position.getMoneyValue(Position.In.LOCAL))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("realisedGain", previousGain.add(new BigDecimal("60.99")))
    ;

  }
}
