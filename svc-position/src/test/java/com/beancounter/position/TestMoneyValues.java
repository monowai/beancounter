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
import com.beancounter.common.utils.BcJson;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.position.accumulation.BuyBehaviour;
import com.beancounter.position.accumulation.DividendBehaviour;
import com.beancounter.position.accumulation.SellBehaviour;
import com.beancounter.position.accumulation.SplitBehaviour;
import com.beancounter.position.service.Accumulator;
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

    Trn buyTrn = new Trn(TrnType.BUY, microsoft);
    buyTrn.setTradeAmount(new BigDecimal("2000.00"));
    buyTrn.setQuantity(new BigDecimal(100));
    buyTrn.setTradeBaseRate(new BigDecimal("1.00"));
    buyTrn.setTradeCashRate(new BigDecimal("10.00"));
    buyTrn.setTradePortfolioRate(TRADE_PORTFOLIO_RATE);

    BuyBehaviour buyBehaviour = new BuyBehaviour();
    Positions positions = new Positions(PortfolioUtils.getPortfolio("TEST"));
    Position position = positions.get(microsoft);
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

    Trn diviTrn = new Trn(TrnType.DIVI, microsoft);
    diviTrn.setTradeDate(new DateUtils().getDate());
    diviTrn.setTradeAmount(BigDecimal.TEN);
    diviTrn.setCashAmount(BigDecimal.TEN);
    diviTrn.setTradeBaseRate(BigDecimal.ONE);
    diviTrn.setTradeCashRate(BigDecimal.TEN);
    diviTrn.setTradePortfolioRate(TRADE_PORTFOLIO_RATE);

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


    DateUtils dateUtils = new DateUtils();
    assertThat(dateUtils.isToday(
        dateUtils.getDateString(position.getDateValues().getLastDividend())));

    Trn splitTrn = new Trn(TrnType.SPLIT, microsoft);
    splitTrn.setQuantity(BigDecimal.TEN);
    splitTrn.setCashAmount(BigDecimal.TEN);
    splitTrn.setTradeBaseRate(BigDecimal.ONE);
    splitTrn.setTradeCashRate(BigDecimal.TEN);
    splitTrn.setTradePortfolioRate(TRADE_PORTFOLIO_RATE);

    new SplitBehaviour().accumulate(splitTrn, positions.getPortfolio(), position);

    MoneyValues tradeValues = position.getMoneyValues(Position.In.TRADE);
    assertThat(tradeValues).isNotNull();

    byte[] bytes = BcJson.getObjectMapper().writeValueAsBytes(position);
    Position deepCopy = BcJson.getObjectMapper().readValue(bytes, Position.class);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValues(Position.In.TRADE).getCostBasis());

    tradeValues = position.getMoneyValues(Position.In.BASE);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValues(Position.In.BASE).getCostBasis());

    tradeValues = position.getMoneyValues(Position.In.PORTFOLIO);
    assertThat(tradeValues.getCostBasis())
        .isEqualTo(deepCopy.getMoneyValues(Position.In.PORTFOLIO).getCostBasis());

    Trn sellTrn = new Trn(TrnType.SELL, microsoft);
    sellTrn.setTradeAmount(new BigDecimal("4000.00"));
    sellTrn.setQuantity(position.getQuantityValues().getTotal()); // Sell All
    sellTrn.setTradeBaseRate(BigDecimal.ONE);
    sellTrn.setTradeCashRate(BigDecimal.TEN);
    sellTrn.setTradePortfolioRate(TRADE_PORTFOLIO_RATE);

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

    Positions positions = new Positions(PortfolioUtils.getPortfolio("TEST"));

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Trn buy = new Trn(TrnType.BUY, microsoft);
    buy.setTradeAmount(new BigDecimal(2000));
    buy.setQuantity(new BigDecimal(100));

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
    Positions positions = new Positions(PortfolioUtils.getPortfolio("TEST"));

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Trn buy = new Trn(TrnType.BUY, microsoft);
    buy.setTradeAmount(new BigDecimal(2000));
    buy.setQuantity(new BigDecimal(100));

    position = accumulator.accumulate(buy, positions.getPortfolio(), position);
    positions.add(position);

    position = positions.get(microsoft);

    Trn sell = new Trn(TrnType.SELL, microsoft);
    sell.setTradeAmount(new BigDecimal(2000));
    sell.setQuantity(new BigDecimal(50));

    position = accumulator.accumulate(sell, positions.getPortfolio(), position);

    assertThat(position.getQuantityValues().getTotal()).isEqualTo(BigDecimal.valueOf(50));

    assertThat(position.getMoneyValues(Position.In.TRADE).getRealisedGain())
        .isEqualTo(new BigDecimal("1000.00"));

  }

  @Test
  void is_RealisedGainWithSignedQuantitiesCalculated() {

    Positions positions = new Positions(PortfolioUtils.getPortfolio("TEST"));

    Position position = positions.get(bidu);

    assertThat(position)
        .isNotNull();

    Trn buy = new Trn(TrnType.BUY, bidu);
    buy.setTradeAmount(new BigDecimal("1695.02"));
    buy.setQuantity(new BigDecimal("8"));

    Portfolio portfolio = getPortfolio("TEST");
    position = accumulator.accumulate(buy, portfolio, position);
    positions.add(position);

    position = positions.get(bidu);

    buy = new Trn(TrnType.BUY, bidu);
    buy.setTradeAmount(new BigDecimal("405.21"));
    buy.setQuantity(new BigDecimal("2"));

    position = accumulator.accumulate(buy, portfolio, position);

    MoneyValues localMoney = position.getMoneyValues(Position.In.TRADE);

    assertThat(position.getQuantityValues().getTotal().multiply(localMoney.getAverageCost())
        .setScale(2, RoundingMode.HALF_UP))
        .isEqualTo(localMoney.getCostBasis());


    Trn sell = new Trn(TrnType.SELL, bidu);
    sell.setTradeAmount(new BigDecimal("841.63"));
    sell.setQuantity(new BigDecimal("-3"));

    position = accumulator.accumulate(sell, portfolio, position);

    assertThat(position.getQuantityValues().getTotal()
        .multiply(localMoney.getAverageCost()).setScale(2, RoundingMode.HALF_UP))
        .isEqualTo(localMoney.getCostValue());

    assertThat(localMoney)
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("sales", new BigDecimal("841.63"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("211.56"))
    ;

    sell = new Trn(TrnType.SELL, bidu);
    sell.setTradeAmount(new BigDecimal("1871.01"));
    sell.setQuantity(new BigDecimal("-7"));

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

    Positions positions = new Positions(PortfolioUtils.getPortfolio("TEST"));

    Position position = positions.get(microsoft);

    assertThat(position)
        .isNotNull();

    Trn buy = new Trn(TrnType.BUY, microsoft);
    buy.setTradeAmount(new BigDecimal("1695.02"));
    buy.setQuantity(new BigDecimal("8"));

    position = accumulator.accumulate(buy, positions.getPortfolio(), position);
    positions.add(position);

    buy = new Trn(TrnType.BUY, microsoft);
    buy.setTradeAmount(new BigDecimal("405.21"));
    buy.setQuantity(new BigDecimal("2"));

    accumulator.accumulate(buy, positions.getPortfolio(), position);
    position = positions.get(microsoft);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.TEN);

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("210.023"))
        .hasFieldOrPropertyWithValue("realisedGain", BigDecimal.ZERO)
    ;

    Trn sell = new Trn(TrnType.SELL, microsoft);
    sell.setTradeAmount(new BigDecimal("841.63"));
    sell.setQuantity(new BigDecimal("3.0"));

    accumulator.accumulate(sell, positions.getPortfolio(), position);

    // Sell does not affect the cost basis or average cost, but it will create a signed gain
    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", new BigDecimal("2100.23"))
        .hasFieldOrPropertyWithValue("averageCost", new BigDecimal("210.023"))
        .hasFieldOrPropertyWithValue("realisedGain", new BigDecimal("211.56"))
    ;

    sell = new Trn(TrnType.SELL, microsoft);
    sell.setTradeAmount(new BigDecimal("1871.01"));
    sell.setQuantity(new BigDecimal("7"));

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
    Positions positions = new Positions(PortfolioUtils.getPortfolio("TEST"));

    Position position = positions.get(intel);

    assertThat(position)
        .isNotNull();

    Trn buy = new Trn(TrnType.BUY, intel);
    buy.setTradeAmount(new BigDecimal("2646.08"));
    buy.setQuantity(new BigDecimal("80"));

    position = accumulator.accumulate(buy, positions.getPortfolio(), position);
    positions.add(position);

    Trn sell = new Trn(TrnType.SELL, intel);
    sell.setTradeAmount(new BigDecimal("2273.9"));
    sell.setQuantity(new BigDecimal("80"));

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
    buy = new Trn(TrnType.BUY, intel);
    buy.setTradeAmount(new BigDecimal("1603.32"));
    buy.setQuantity(new BigDecimal("60"));

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

    sell = new Trn(TrnType.SELL, intel);
    sell.setTradeAmount(new BigDecimal("1664.31"));
    sell.setQuantity(new BigDecimal("60"));

    MoneyValues tradeMoney = position.getMoneyValues(Position.In.TRADE);
    assertThat(tradeMoney).isNotNull();
    BigDecimal previousGain = tradeMoney.getRealisedGain(); // Track the previous gain

    accumulator.accumulate(sell, positions.getPortfolio(), position);

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("realisedGain", previousGain.add(new BigDecimal("60.99")))
    ;

  }


}
