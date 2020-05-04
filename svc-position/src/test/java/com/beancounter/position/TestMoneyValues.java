package com.beancounter.position;

import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.position.accumulation.BuyBehaviour;
import com.beancounter.position.accumulation.DividendBehaviour;
import com.beancounter.position.accumulation.SellBehaviour;
import com.beancounter.position.accumulation.SplitBehaviour;
import com.beancounter.position.service.Accumulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(classes = Accumulator.class)
class TestMoneyValues {
  private static final BigDecimal TRADE_PORTFOLIO_RATE = new BigDecimal("100.00");
  private final Asset microsoft = AssetUtils.getAsset("NYSE", "MSFT");
  private final Asset intel = AssetUtils.getAsset("NYSE", "INTC");
  private final Asset bidu = AssetUtils.getAsset("NYSE", "BIDU");

  @Autowired
  private Accumulator accumulator;

  /**
   * Tests the lifecycle of a transaction over all supported transaction types and verifies
   * key values in the various currency buckets.
   *
   * <p>Possibly the most important unit test in the suite.
   *
   * <p>Simple FX values make assertions easier to calculate.
   */
  @Test
  void is_ValuedInTrackedCurrencies() throws IOException {
    Positions positions = new Positions(Portfolio.builder().code("TEST").build());
    Position position = positions.get(microsoft);

    Trn buyTrn = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("2000.00"))
        .quantity(new BigDecimal(100))
        .tradeBaseRate(new BigDecimal("1.00"))
        .tradeCashRate(new BigDecimal("10.00"))
        .tradePortfolioRate(TRADE_PORTFOLIO_RATE)
        .build();

    BuyBehaviour buyBehaviour = new BuyBehaviour();
    buyBehaviour.accumulate(buyTrn, positions.getPortfolio(), position);
    assertThat(position.getQuantityValues().getTotal())
        .isEqualTo(new BigDecimal("100"));

    assertThat(position.getMoneyValues(Position.In.TRADE).getPurchases())
        .isEqualTo(new BigDecimal("2000.00"));
    assertThat(position.getMoneyValues(Position.In.TRADE).getCostBasis())
        .isEqualTo(new BigDecimal("2000.00"));

    assertThat(position.getMoneyValues(Position.In.BASE).getPurchases())
        .isEqualTo(new BigDecimal("2000.00"));
    assertThat(position.getMoneyValues(Position.In.BASE).getCostBasis())
        .isEqualTo(new BigDecimal("2000.00"));

    assertThat(position.getMoneyValues(Position.In.PORTFOLIO).getCostBasis())
        .isEqualTo(new BigDecimal("20.00"));
    assertThat(position.getMoneyValues(Position.In.PORTFOLIO).getPurchases())
        .isEqualTo(new BigDecimal("20.00"));

    Trn diviTrn = Trn.builder()
        .trnType(TrnType.DIVI)
        .asset(microsoft)
        .tradeAmount(BigDecimal.TEN)
        .cashAmount(BigDecimal.TEN)
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCashRate(BigDecimal.TEN)
        .tradePortfolioRate(TRADE_PORTFOLIO_RATE)
        .build();

    DividendBehaviour dividendBehaviour = new DividendBehaviour();
    dividendBehaviour.accumulate(diviTrn, positions.getPortfolio(), position);
    assertThat(position.getQuantityValues().getTotal())
        .isEqualTo(new BigDecimal("100"));

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("dividends", new BigDecimal("10.00"));

    assertThat(position.getMoneyValues(Position.In.BASE))
        .hasFieldOrPropertyWithValue("dividends", new BigDecimal("10.00"));

    assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
        .hasFieldOrPropertyWithValue("dividends", new BigDecimal(".10"));

    ObjectMapper objectMapper = new ObjectMapper();
    String bytes = objectMapper.writeValueAsString(position);
    Position deepCopy = objectMapper.readValue(bytes, Position.class);

    SplitBehaviour splitBehaviour = new SplitBehaviour();
    Trn splitTrn = Trn.builder()
        .trnType(TrnType.DIVI)
        .asset(microsoft)
        .quantity(BigDecimal.TEN)
        .cashAmount(BigDecimal.TEN)
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCashRate(BigDecimal.TEN)
        .tradePortfolioRate(TRADE_PORTFOLIO_RATE)
        .build();

    splitBehaviour.accumulate(splitTrn, positions.getPortfolio(), position);

    MoneyValues tradeValues = position.getMoneyValues(Position.In.TRADE);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValues(Position.In.TRADE).getCostBasis());

    tradeValues = position.getMoneyValues(Position.In.BASE);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValues(Position.In.BASE).getCostBasis());

    tradeValues = position.getMoneyValues(Position.In.PORTFOLIO);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValues(Position.In.PORTFOLIO).getCostBasis());

    Trn sellTrn = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("4000.00"))
        .quantity(position.getQuantityValues().getTotal()) // Sell all
        .tradeBaseRate(BigDecimal.ONE)
        .tradeCashRate(BigDecimal.TEN)
        .tradePortfolioRate(TRADE_PORTFOLIO_RATE)
        .build();

    SellBehaviour sellBehaviour = new SellBehaviour();
    sellBehaviour.accumulate(sellTrn, positions.getPortfolio(), position);

    assertThat(position.getMoneyValues(Position.In.TRADE).getSales())
        .isEqualTo(new BigDecimal("4000.00"));
    assertThat(position.getMoneyValues(Position.In.TRADE).getRealisedGain())
        .isEqualTo(new BigDecimal("2000.00"));

    assertThat(position.getMoneyValues(Position.In.BASE).getSales())
        .isEqualTo(new BigDecimal("4000.00"));
    assertThat(position.getMoneyValues(Position.In.BASE).getRealisedGain())
        .isEqualTo(new BigDecimal("2000.00"));

    assertThat(position.getMoneyValues(Position.In.PORTFOLIO).getSales())
        .isEqualTo(new BigDecimal("40.00"));
    assertThat(position.getMoneyValues(Position.In.PORTFOLIO).getRealisedGain())
        .isEqualTo(new BigDecimal("20.00"));

  }

  @Test
  void is_QuantityAndMarketValueCalculated() {

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Trn buy = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Portfolio portfolio = getPortfolio("TEST");
    position = accumulator.accumulate(buy, portfolio, position);
    positions.add(position);

    position = positions.get(microsoft);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("purchased", new BigDecimal(100))
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100));


  }

  @Test
  void is_RealisedGainCalculated() {
    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Trn buy = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    position = accumulator.accumulate(buy, positions.getPortfolio(), position);
    positions.add(position);

    position = positions.get(microsoft);

    Trn sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(50)).build();

    position = accumulator.accumulate(sell, positions.getPortfolio(), position);

    assertThat(position.getQuantityValues().getTotal()).isEqualTo(BigDecimal.valueOf(50));

    assertThat(position.getMoneyValues(Position.In.TRADE).getRealisedGain())
        .isEqualTo(new BigDecimal("1000.00"));

  }

  @Test
  void is_RealisedGainWithSignedQuantitiesCalculated() {

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(bidu);

    assertThat(position)
        .isNotNull();

    Trn buy = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(bidu)
        .tradeAmount(new BigDecimal("1695.02"))
        .quantity(new BigDecimal("8"))
        .build();

    Portfolio portfolio = getPortfolio("TEST");
    position = accumulator.accumulate(buy, portfolio, position);
    positions.add(position);

    position = positions.get(bidu);

    buy = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(bidu)
        .tradeAmount(new BigDecimal("405.21"))
        .quantity(new BigDecimal("2"))
        .build();

    position = accumulator.accumulate(buy, portfolio, position);

    MoneyValues localMoney = position.getMoneyValues(Position.In.TRADE);

    assertThat(position.getQuantityValues().getTotal().multiply(localMoney.getAverageCost())
        .setScale(2, RoundingMode.HALF_UP))
        .isEqualTo(localMoney.getCostBasis());


    Trn sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(bidu)
        .tradeAmount(new BigDecimal("841.63"))
        .quantity(new BigDecimal("-3")).build();

    position = accumulator.accumulate(sell, portfolio, position);

    assertThat(position.getQuantityValues().getTotal()
        .multiply(localMoney.getAverageCost()).setScale(2, RoundingMode.HALF_UP))
        .isEqualTo(localMoney.getCostValue());

    assertThat(localMoney)
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("sales", new BigDecimal("841.63"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("211.56"))
    ;

    sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(bidu)
        .tradeAmount(new BigDecimal("1871.01"))
        .quantity(new BigDecimal("-7")).build();

    position = accumulator.accumulate(sell, portfolio, position);

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("sales", new BigDecimal("2712.64"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("612.41"))
    ;

    assertThat(position.getQuantityValues().getTotal()).isEqualTo(BigDecimal.ZERO);

  }

  @Test
  void is_RealisedGainAfterSellingToZeroCalculated() {

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Trn buy = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("1695.02"))
        .quantity(new BigDecimal("8")).build();

    position = accumulator.accumulate(buy, positions.getPortfolio(), position);
    positions.add(position);

    buy = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("405.21"))
        .quantity(new BigDecimal("2")).build();

    accumulator.accumulate(buy, positions.getPortfolio(), position);

    position = positions.get(microsoft);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.TEN);

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("210.023"))
        .hasFieldOrPropertyWithValue("realisedGain", BigDecimal.ZERO)
    ;

    Trn sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("841.63"))
        .quantity(new BigDecimal("3.0")).build();

    accumulator.accumulate(sell, positions.getPortfolio(), position);

    // Sell does not affect the cost basis or average cost, but it will create a signed gain
    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("210.023"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("211.56"))
    ;

    sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(microsoft)
        .tradeAmount(new BigDecimal("1871.01"))
        .quantity(new BigDecimal("7")).build();

    accumulator.accumulate(sell, positions.getPortfolio(), position);

    // Sell down to 0; reset cost basis
    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("0"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("0"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("612.41"))
    ;
  }

  @Test
  void is_RealisedGainAfterReenteringAPositionCalculated() {
    Positions positions = new Positions(Portfolio.builder().code("TEST").build());

    Position position = positions.get(intel);

    assertThat(position)
        .isNotNull();

    Trn buy = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(intel)
        .tradeAmount(new BigDecimal("2646.08"))
        .quantity(new BigDecimal("80")).build();

    position = accumulator.accumulate(buy, positions.getPortfolio(), position);
    positions.add(position);

    Trn sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(intel)
        .tradeAmount(new BigDecimal("2273.9"))
        .quantity(new BigDecimal("80")).build();

    accumulator.accumulate(sell, positions.getPortfolio(), position);

    position = positions.get(intel);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO);

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("-372.18"))
    ;

    // Re-enter the position
    buy = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(intel)
        .tradeAmount(new BigDecimal("1603.32"))
        .quantity(new BigDecimal("60")).build();

    accumulator.accumulate(buy, positions.getPortfolio(), position);

    position = positions.get(intel);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", buy.getQuantity());

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", buy.getTradeAmount())
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("26.722"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("-372.18"))
    ;

    // Second sell taking us back to zero. Verify that the accumulated gains.

    sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(intel)
        .tradeAmount(new BigDecimal("1664.31"))
        .quantity(new BigDecimal("60")).build();

    BigDecimal previousGain = position.getMoneyValues(Position.In.TRADE)
        .getRealisedGain(); // Track the previous gain

    accumulator.accumulate(sell, positions.getPortfolio(), position);

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("realisedGain", previousGain.add(new BigDecimal("60.99")))
    ;

  }


}
