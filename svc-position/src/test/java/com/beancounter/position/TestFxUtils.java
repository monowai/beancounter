package com.beancounter.position;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.position.utils.FxUtils;
import org.junit.jupiter.api.Test;

class TestFxUtils {
  private final FxUtils fxUtils = new FxUtils();
  private final CurrencyUtils currencyUtils = new CurrencyUtils();

  @Test
  void is_CurrencyPairResultsAsExpected() {

    Position validPosition = new Position(getAsset("USD", "Test"));
    Currency validCurrency = validPosition.getAsset().getMarket().getCurrency();

    assertThat(IsoCurrencyPair.toPair(currencyUtils.getCurrency("NZD"), validCurrency))
        .isNotNull();// From != To
  }

  @Test
  void is_FxRequestCorrect() {
    Currency usd = currencyUtils.getCurrency("USD");
    Market gbpMarket = new Market("GBP", currencyUtils.getCurrency("GBP"));
    Position gbpPosition = new Position(getAsset(gbpMarket, "GBP Asset"));

    Market usdMarket = new Market("USD", currencyUtils.getCurrency("USD"));
    Position usdPosition = new Position(getAsset(usdMarket, "USD Asset"));

    Position otherUsdPosition = new Position(
        getAsset(usdMarket, "USD Asset Other"));

    Portfolio portfolio = new Portfolio("ABC", currencyUtils.getCurrency("SGD"));
    Positions positions = new Positions(portfolio);
    positions.add(gbpPosition);
    positions.add(usdPosition);
    positions.add(otherUsdPosition);

    FxRequest fxRequest = fxUtils.buildRequest(usd, positions);
    assertThat(fxRequest.getPairs()).hasSize(3)
        .containsOnly(
            new IsoCurrencyPair("SGD", "USD"), // PF:TRADE
            new IsoCurrencyPair("SGD", "GBP"), // PF:TRADE
            new IsoCurrencyPair("USD", "GBP"));  // BASE:TRADE
  }
}
