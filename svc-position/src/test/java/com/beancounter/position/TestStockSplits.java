package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.accumulation.Buy;
import com.beancounter.position.accumulation.Sell;
import com.beancounter.position.accumulation.Split;
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

    Asset apple = getAsset("AAPL", "NASDAQ");

    Positions positions = new Positions(getPortfolio("TEST"));

    Position position = positions.get(apple);

    assertThat(position)
        .isNotNull();

    LocalDate today = LocalDate.now();

    Transaction buyTrn = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(apple)
        .tradeAmount(new BigDecimal("2000"))
        .tradeDate(DateUtils.convert(today))
        .quantity(new BigDecimal("100")).build();

    Buy buy = new Buy();
    buy.value(buyTrn, positions.getPortfolio(), position);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(100))
    ;

    BigDecimal costBasis = position.getMoneyValues(Position.In.TRADE).getCostBasis();

    Transaction stockSplit = Transaction.builder()
        .trnType(TrnType.SPLIT)
        .asset(apple)
        .tradeDate(DateUtils.convert(today))
        .quantity(new BigDecimal("7"))
        .build();

    Split split = new Split();
    split.value(stockSplit, positions.getPortfolio(), position);

    // 7 for one split
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(700));

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("costBasis", costBasis);

    // Another buy at the adjusted price
    buy.value(buyTrn, positions.getPortfolio(), position);

    // 7 for one split
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", new BigDecimal(800));

    Transaction sell = Transaction.builder()
        .trnType(TrnType.SELL)
        .asset(apple)
        .tradeAmount(new BigDecimal("2000"))
        .tradeDate(DateUtils.convert(today))
        .quantity(new BigDecimal("800")).build();

    // Sell the entire position
    new Sell().value(sell, positions.getPortfolio(), position);

    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO)
    ;

    // Repurchase; total should be equal to the quantity we just purchased
    buy.value(buyTrn, getPortfolio("TEST"), position);
    assertThat(position.getQuantityValues())
        .hasFieldOrPropertyWithValue("total", buyTrn.getQuantity());

  }

}
