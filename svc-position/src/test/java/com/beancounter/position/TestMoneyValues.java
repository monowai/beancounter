package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.position.accumulation.Buy;
import com.beancounter.position.accumulation.Dividend;
import com.beancounter.position.accumulation.Sell;
import com.beancounter.position.accumulation.Split;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import com.beancounter.position.service.Accumulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;


class TestMoneyValues {
  private static final BigDecimal TRADE_PORTFOLIO_RATE = new BigDecimal("100");
  private Asset microsoft = getAsset("MSFT", "NYSE");
  private Asset intel = getAsset("INTC", "NYSE");
  private Asset bidu = getAsset("BIDU", "NYSE");

  /**
   * Tests the lifecycle of a transaction over all supported transaction types and verifies
   * key values in the various currency buckets.
   *
   * <p>Possibly the most important unit test in the suite.
   *
   * <p>Simple FX values make assertions easier to calculate.
   */
  @Test
  @VisibleForTesting
  void is_ValuedInTrackedCurrencies() throws IOException {
    Positions positions = new Positions(Portfolio.builder().code("TEST").build());
    Position position = positions.get(microsoft);

    Transaction buyTrn = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100))
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCashRate(BigDecimal.TEN)
        .tradePortfolioRate(TRADE_PORTFOLIO_RATE)
        .build();

    Buy buy = new Buy();
    buy.value(buyTrn, position);
    assertThat(position.getQuantityValues().getTotal())
        .isEqualTo(new BigDecimal("100"));

    assertThat(position.getMoneyValue(Position.In.TRADE).getPurchases())
        .isEqualTo(new BigDecimal("2000.00"));
    assertThat(position.getMoneyValue(Position.In.TRADE).getCostBasis())
        .isEqualTo(new BigDecimal("2000.00"));

    assertThat(position.getMoneyValue(Position.In.BASE).getPurchases())
        .isEqualTo(new BigDecimal("2000.00"));
    assertThat(position.getMoneyValue(Position.In.BASE).getCostBasis())
        .isEqualTo(new BigDecimal("2000.00"));

    assertThat(position.getMoneyValue(Position.In.CASH).getPurchases())
        .isEqualTo(new BigDecimal("20000.00"));
    assertThat(position.getMoneyValue(Position.In.CASH).getCostBasis())
        .isEqualTo(new BigDecimal("20000.00"));

    assertThat(position.getMoneyValue(Position.In.PORTFOLIO).getPurchases())
        .isEqualTo(new BigDecimal("200000.00"));
    assertThat(position.getMoneyValue(Position.In.PORTFOLIO).getCostBasis())
        .isEqualTo(new BigDecimal("200000.00"));

    Transaction diviTrn = Transaction.builder()
        .trnType(TrnType.DIVI)
        .asset(microsoft)
        .tradeAmount(BigDecimal.TEN)
        .cashAmount(BigDecimal.TEN)
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCashRate(BigDecimal.TEN)
        .tradePortfolioRate(TRADE_PORTFOLIO_RATE)
        .build();

    Dividend dividend = new Dividend();
    dividend.value(diviTrn, position);
    assertThat(position.getQuantityValues().getTotal()).isEqualTo(new BigDecimal("100"));

    assertThat(position.getMoneyValue(Position.In.TRADE).getDividends())
        .isEqualTo(new BigDecimal("10.00"));

    assertThat(position.getMoneyValue(Position.In.BASE).getDividends())
        .isEqualTo(new BigDecimal("10.00"));

    assertThat(position.getMoneyValue(Position.In.CASH).getDividends())
        .isEqualTo(new BigDecimal("100.00"));

    assertThat(position.getMoneyValue(Position.In.PORTFOLIO).getDividends())
        .isEqualTo(new BigDecimal("1000.00"));

    ObjectMapper objectMapper = new ObjectMapper();
    String bytes = objectMapper.writeValueAsString(position);
    Position deepCopy = objectMapper.readValue(bytes, Position.class);

    Split split = new Split();
    Transaction splitTrn = Transaction.builder()
        .trnType(TrnType.DIVI)
        .asset(microsoft)
        .quantity(BigDecimal.TEN)
        .cashAmount(BigDecimal.TEN)
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCashRate(BigDecimal.TEN)
        .tradePortfolioRate(TRADE_PORTFOLIO_RATE)
        .build();

    split.value(splitTrn, position);

    MoneyValues tradeValues = position.getMoneyValue(Position.In.TRADE);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValue(Position.In.TRADE).getCostBasis());

    tradeValues = position.getMoneyValue(Position.In.BASE);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValue(Position.In.BASE).getCostBasis());

    tradeValues = position.getMoneyValue(Position.In.PORTFOLIO);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValue(Position.In.PORTFOLIO).getCostBasis());

    tradeValues = position.getMoneyValue(Position.In.CASH);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValue(Position.In.CASH).getCostBasis());

    Transaction sellTrn = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(4000))
        .quantity(position.getQuantityValues().getTotal()) // Sell all
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCashRate(BigDecimal.TEN)
        .tradePortfolioRate(TRADE_PORTFOLIO_RATE)
        .build();

    Sell sell = new Sell();
    sell.value(sellTrn, position);

    assertThat(position.getMoneyValue(Position.In.TRADE).getSales())
        .isEqualTo(new BigDecimal("4000.00"));
    assertThat(position.getMoneyValue(Position.In.TRADE).getRealisedGain())
        .isEqualTo(new BigDecimal("2000.00"));

    assertThat(position.getMoneyValue(Position.In.BASE).getSales())
        .isEqualTo(new BigDecimal("4000.00"));
    assertThat(position.getMoneyValue(Position.In.BASE).getRealisedGain())
        .isEqualTo(new BigDecimal("2000.00"));

    assertThat(position.getMoneyValue(Position.In.CASH).getSales())
        .isEqualTo(new BigDecimal("40000.00"));
    assertThat(position.getMoneyValue(Position.In.CASH).getRealisedGain())
        .isEqualTo(new BigDecimal("20000.00"));

    assertThat(position.getMoneyValue(Position.In.PORTFOLIO).getSales())
        .isEqualTo(new BigDecimal("400000.00"));
    assertThat(position.getMoneyValue(Position.In.PORTFOLIO).getRealisedGain())
        .isEqualTo(new BigDecimal("200000.00"));

  }

  @Test
  @VisibleForTesting
  void is_QuantityAndMarketValueCalculated() {

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator();
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
    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator(
    );

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

    assertThat(position.getMoneyValue(Position.In.TRADE).getRealisedGain())
        .isEqualTo(new BigDecimal("1000.00"));

  }

  @Test
  @VisibleForTesting
  void is_RealisedGainWithSignedQuantitiesCalculated() {

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

    Accumulator accumulator = new Accumulator();

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

    MoneyValues localMoney = position.getMoneyValue(Position.In.TRADE);

    assertThat(position.getQuantityValues().getTotal().multiply(localMoney.getAverageCost())
        .setScale(2, RoundingMode.HALF_UP))
        .isEqualTo(localMoney.getCostBasis());


    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(bidu)
        .tradeAmount(new BigDecimal("841.63"))
        .quantity(new BigDecimal("-3")).build();

    position = accumulator.accumulate(sell, position);

    assertThat(position.getQuantityValues().getTotal()
        .multiply(localMoney.getAverageCost()).setScale(2, RoundingMode.HALF_UP))
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

    assertThat(position.getMoneyValue(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("sales", new BigDecimal("2712.64"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("612.41"))
    ;

    assertThat(position.getQuantityValues().getTotal()).isEqualTo(BigDecimal.ZERO);

  }

  @Test
  @VisibleForTesting
  void is_RealisedGainAfterSellingToZeroCalculated() {

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("1695.02"))
        .quantity(new BigDecimal("8")).build();

    Accumulator accumulator = new Accumulator(
    );

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

    assertThat(position.getMoneyValue(Position.In.TRADE))
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
    assertThat(position.getMoneyValue(Position.In.TRADE))
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
    assertThat(position.getMoneyValue(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("0"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("0"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("612.41"))
    ;
  }

  @Test
  @VisibleForTesting
  void is_RealisedGainAfterReenteringAPositionCalculated() {
    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(intel);

    assertThat(position)
        .isNotNull();

    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(intel)
        .tradeAmount(new BigDecimal("2646.08"))
        .quantity(new BigDecimal("80")).build();

    Accumulator accumulator = new Accumulator();

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

    assertThat(position.getMoneyValue(Position.In.TRADE))
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

    assertThat(position.getMoneyValue(Position.In.TRADE))
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

    BigDecimal previousGain = position.getMoneyValue(Position.In.TRADE)
        .getRealisedGain(); // Track the previous gain

    accumulator.accumulate(sell, position);

    assertThat(position.getMoneyValue(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("realisedGain", previousGain.add(new BigDecimal("60.99")))
    ;

  }


}
