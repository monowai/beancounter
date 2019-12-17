package com.beancounter.common;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.DateValues;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestPositions {
  private ObjectMapper mapper = new ObjectMapper();

  @Test
  void is_PositionResponseChainSerializing() throws Exception {
    Map<Position.In, MoneyValues> moneyValuesMap = new HashMap<>();
    moneyValuesMap.put(Position.In.TRADE, MoneyValues.builder()
        .dividends(new BigDecimal("100"))
        .build());

    Positions positions = new Positions(Portfolio.builder()
        .code("T")
        .currency(Currency.builder().code("SGD").build())
        .build());

    Asset asset = getAsset("TEST", "TEST");
    positions.add(Position.builder()
        .asset(asset)
        .moneyValues(moneyValuesMap)
        .quantityValues(QuantityValues.builder()
            .purchased(new BigDecimal(200))
            .build())
        .build()
    );
    Position position = positions.get(asset);

    DateValues dateValues = DateValues.builder()
        .opened(DateUtils.today())
        .closed(DateUtils.today())
        .last(DateUtils.today())
        .build();

    position.setDateValues(dateValues);

    PositionResponse positionResponse = PositionResponse.builder().data(positions).build();

    String json = mapper.writeValueAsString(positionResponse);

    PositionResponse fromJson = mapper.readValue(json, PositionResponse.class);

    assertThat(fromJson).isEqualToComparingFieldByField(positionResponse);
  }

  @Test
  void is_DateValuesSetFromTransaction() {

    Asset asset = getAsset("Dates", "Code");

    Date firstTradeDate = DateUtils.getDate("2018-12-01");
    Transaction firstTrade = Transaction.builder()
        .tradeDate(firstTradeDate)
        .portfolio(getPortfolio("CODE"))
        .asset(asset)
        .build();

    Date secondTradeDate = DateUtils.getDate("2018-12-02");
    Transaction secondTrade = Transaction.builder()
        .tradeDate(secondTradeDate)
        .portfolio(getPortfolio("CODE"))
        .asset(asset)
        .build();

    Positions positions = new Positions(getPortfolio("Twee"));
    Position position = positions.get(firstTrade);
    positions.add(position);
    // Calling this should not set the "first" trade date.
    position = positions.get(secondTrade);

    assertThat(position.getDateValues())
        .hasFieldOrPropertyWithValue("opened", "2018-12-01")
    ;
  }

  @Test
  void is_GetPositionNonNull() {

    Positions positions = new Positions(getPortfolio("Test"));
    Asset asset = getAsset("TEST", "TEST");
    Position position = positions.get(asset);
    assertThat(position).isNotNull().hasFieldOrPropertyWithValue("asset", asset);

  }

  @Test
  void is_MoneyValuesFromPosition() {

    Position position = Position.builder()
        .asset(getAsset("Twee", "Twee"))
        .build();

    // Requesting a non existent MV.  Without a currency, it can't be created
    assertThat(position.getMoneyValues(Position.In.TRADE)).isNull();
    // Retrieve with a currency will create if missing
    assertThat(position.getMoneyValues(Position.In.TRADE, getCurrency("SGD")))
        .isNotNull()
        .hasFieldOrPropertyWithValue("currency", getCurrency("SGD"));

    assertThat(position.getMoneyValues(Position.In.TRADE, getCurrency("SGD")))
        .isNotNull();
  }

  @Test
  void is_PositionRequestSerializing() throws Exception {
    Collection<Transaction> transactions = new ArrayList<>();
    transactions.add(Transaction.builder()
        .asset(AssetUtils.getAsset("Blah", "Market"))
        .portfolio(PortfolioUtils.getPortfolio("PCODE"))
        .build());

    PositionRequest positionRequest = PositionRequest.builder()
        .portfolioId("TWEE")
        .transactions(transactions)
        .build();
    String json = mapper.writeValueAsString(positionRequest);

    PositionRequest fromJson = mapper.readValue(json, PositionRequest.class);
    assertThat(fromJson).isEqualToComparingFieldByField(positionRequest);

  }
}
