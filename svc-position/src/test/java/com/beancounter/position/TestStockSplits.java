package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.accumulation.BuyBehaviour;
import com.beancounter.position.accumulation.SellBehaviour;
import com.beancounter.position.accumulation.SplitBehaviour;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Corporate Actions - Stock Splits.  These do not affect Cost.
 *
 * @author mikeh
 * @since 2019-02-20
 */
class TestStockSplits {

  @Test
  void is_QuantityWorkingForSplit() {

    Asset apple = getAsset("NASDAQ", "AAPL");

    Positions positions = new Positions(getPortfolio("TEST"));

    Position position = positions.get(apple);

    assertThat(position)
        .isNotNull();

    LocalDate today = LocalDate.now();

    Trn buyTrn = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(apple)
        .tradeAmount(new BigDecimal("2000"))
        .tradeDate(new DateUtils().convert(today))
        .quantity(new BigDecimal("100")).build();

    BuyBehaviour buyBehaviour = new BuyBehaviour();
    buyBehaviour.accumulate(buyTrn, positions.getPortfolio(), position);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100))
    ;

    BigDecimal costBasis = position.getMoneyValues(Position.In.TRADE).getCostBasis();

    Trn stockSplit = Trn.builder()
        .trnType(TrnType.SPLIT)
        .asset(apple)
        .tradeDate(new DateUtils().convert(today))
        .quantity(new BigDecimal("7"))
        .build();

    SplitBehaviour splitBehaviour = new SplitBehaviour();
    splitBehaviour.accumulate(stockSplit, positions.getPortfolio(), position);

    // 7 for one split
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(700));

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", costBasis);

    // Another buy at the adjusted price
    buyBehaviour.accumulate(buyTrn, positions.getPortfolio(), position);

    // 7 for one split
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(800));

    Trn sell = Trn.builder()
        .trnType(TrnType.SELL)
        .asset(apple)
        .tradeAmount(new BigDecimal("2000"))
        .tradeDate(new DateUtils().convert(today))
        .quantity(new BigDecimal("800")).build();

    // Sell the entire position
    new SellBehaviour().accumulate(sell, positions.getPortfolio(), position);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO)
    ;

    // Repurchase; total should be equal to the quantity we just purchased
    buyBehaviour.accumulate(buyTrn, getPortfolio("TEST"), position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", buyTrn.getQuantity());

  }

}
