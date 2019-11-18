package com.beancounter.position;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.position.service.Accumulator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;


class TestPositions {

  @Test
  void is_CashDividendAccumulated() {

    Asset asset = AssetUtils.getAsset("MO", Market.builder()
        .code("ASX")
        .currency(getCurrency("AUD"))
        .build());

    Transaction transaction = Transaction.builder()
        .asset(asset)
        .trnType(TrnType.DIVI)
        .portfolio(getPortfolio("Test"))
        .tradeCashRate(new BigDecimal("0.8988"))
        .tradeAmount(new BigDecimal("12.99"))
        .build();

    Accumulator accumulator = new Accumulator();

    Positions positions = new Positions(Portfolio.builder().code("test").build());
    Position position = positions.get(asset);
    accumulator.accumulate(transaction, position);
    assertThat(position.getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("dividends", transaction.getTradeAmount());

  }
}
